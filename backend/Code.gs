/**
 * Google Apps Script Web App for the Nursery app. POST actions, all authorised by a shared
 * secret stored in Script Properties (key SHARED_SECRET):
 *   - getPlants       -> reads the "Plants" sheet, returns plant objects (the manual "Update plant list")
 *   - replacePlants   -> full-mirror rewrite of the "Plants" sheet from Access (in-stock plants only)
 *   - appendSales     -> appends sales rows to "Sales" (deduped by receipt #), stamping each newly
 *                        appended row's sync_status "Pending" for the Access reverse sync (auto-export/push)
 *   - appendCulls     -> appends cull rows to "Culls" (deduped by cull_id), stamping sync_status
 *   - pendingSales    -> returns every "Sales" row whose sync_status is "Pending" (Access reverse sync)
 *   - markSalesSynced -> sets sync_status by (receipt,item_seq) key (Access reverse sync, status-agnostic)
 *   - pendingCulls    -> returns every "Culls" row whose sync_status is "Pending" (Access reverse sync)
 *   - markCullsSynced -> sets sync_status by cull_id key (Access reverse sync, status-agnostic)
 *
 * Every action also stamps a "SyncStatus" sheet (one row per event) with the last time it ran, so the
 * Sheet shows when plants were last pushed from Access and last pulled to / pushed from the device.
 *
 * Requires a second script file `shared.gs` containing the contents of shared.js.
 */

function doPost(e) {
  try {
    var body = JSON.parse(e.postData.contents);
    var secret = getSecret_();
    if (!isAuthorized(body, secret)) {
      return json_({ ok: false, error: 'Unauthorized' });
    }
    switch (body.action) {
      case 'getPlants': return handleGetPlants_();
      case 'replacePlants': return handleReplacePlants_(body);
      case 'appendSales': return handleAppendSales_(body);
      case 'appendCulls': return handleAppendCulls_(body);
      case 'pendingSales': return handlePendingSales_();
      case 'markSalesSynced': return handleMarkSalesSynced_(body);
      case 'pendingCulls': return handlePendingCulls_();
      case 'markCullsSynced': return handleMarkCullsSynced_(body);
      default: return json_({ ok: false, error: 'Unknown action: ' + body.action });
    }
  } catch (err) {
    // Don't echo internal error text (sheet names, runtime messages) to the caller — this runs
    // around JSON.parse and the auth check, so it's reachable before a request is even authorised.
    console.error(err);
    return json_({ ok: false, error: 'Bad request' });
  }
}

function doGet() {
  return json_({ ok: true, message: 'Nursery backend is running. Use POST.' });
}

function getSecret_() {
  return PropertiesService.getScriptProperties().getProperty('SHARED_SECRET');
}

function handleGetPlants_() {
  // Take the same document lock the writers use, so getPlants can't read the "Plants" sheet in the
  // window between handleReplacePlants_'s clearContents() and its setValues() — a half-written read
  // would return an empty/partial list and wipe the device's local plants on the wholesale replace.
  var lock = LockService.getDocumentLock();
  try {
    lock.waitLock(30000);
  } catch (e) {
    return json_({ ok: false, error: 'Sheet busy, please retry' });
  }
  try {
    var sheet = SpreadsheetApp.getActive().getSheetByName('Plants');
    if (!sheet) return json_({ ok: false, error: 'No "Plants" sheet found' });
    var values = sheet.getDataRange().getValues();
    var plants = parsePlants(values);
    recordSync_('Plant list to device', 'Sheet → device', plants.length + ' plants');
    return json_({ ok: true, plants: plants, count: plants.length, updatedAt: new Date().toISOString() });
  } finally {
    lock.releaseLock();
  }
}

function handleReplacePlants_(body) {
  if (!body.header || !body.rows) return json_({ ok: false, error: 'Missing header/rows' });

  // Serialize against any concurrent push/read of this sheet (same reasoning as appendSales): a
  // clear-then-write must not interleave with another request.
  var lock = LockService.getDocumentLock();
  try {
    lock.waitLock(30000);
  } catch (e) {
    return json_({ ok: false, error: 'Sheet busy, please retry' });
  }
  try {
    var ss = SpreadsheetApp.getActive();
    var sheet = ss.getSheetByName('Plants');
    if (!sheet) sheet = ss.insertSheet('Plants');

    var plan = planPlantReplace(body.header, body.rows);
    // An empty header would make the write a 0-column range (a throw). Reject it BEFORE clearing, so
    // a malformed request can never wipe the live sheet without writing anything back.
    if (plan.header.length === 0) return json_({ ok: false, error: 'Empty header' });
    var accCol = accessionColIndex(plan.header);
    if (accCol < 0) accCol = 0;

    // Build the full output BEFORE wiping anything: clearContents has no rollback, so a bad request
    // or a validation failure must bail out while the sheet is still intact.
    var out = [plan.header].concat(plan.rows);

    // Full mirror: wipe the sheet, then write header + the current in-stock rows. Anything that is
    // no longer in the incoming (stock-filtered) set is gone by construction.
    sheet.clearContents();
    // Force the accession column to plain text BEFORE writing so values aren't coerced (e.g. a
    // numeric accession kept as text for a stable dedup/scan round-trip), same as Sales.
    if (plan.rows.length > 0) forceTextColumn_(sheet, 2, plan.rows.length, accCol);
    sheet.getRange(1, 1, out.length, plan.header.length).setValues(out);

    recordSync_('Plants from Access', 'Access → Sheet', plan.rows.length + ' plants');
    return json_({ ok: true, written: plan.rows.length });
  } finally {
    lock.releaseLock();
  }
}

function handleAppendCulls_(body) {
  if (!body.header || !body.rows) return json_({ ok: false, error: 'Missing header/rows' });

  var lock = LockService.getDocumentLock();
  try {
    lock.waitLock(30000);
  } catch (e) {
    return json_({ ok: false, error: 'Sheet busy, please retry' });
  }
  try {
    var sheet = getOrCreateCullsSheet_(body.header);
    var cullCol = body.header.indexOf('cull_id');
    if (cullCol < 0) cullCol = 0;
    var lastRow = sheet.getLastRow();
    var existing = [];
    if (lastRow > 1) {
      existing = sheet.getRange(2, cullCol + 1, lastRow - 1, 1)
        .getValues().map(function (r) { return String(r[0]); });
    }
    var result = filterNewRows(body.rows, existing, cullCol);
    if (result.rows.length > 0) {
      var startRow = sheet.getLastRow() + 1;
      forceTextColumn_(sheet, startRow, result.rows.length, cullCol);
      forceTextColumn_(sheet, startRow, result.rows.length, body.header.indexOf('accession'));
      sheet.getRange(startRow, 1, result.rows.length, body.header.length).setValues(result.rows);
      stampPending_(sheet, startRow, result.rows.length);
    }
    recordSync_('Culls from device', 'device → Sheet',
      'appended ' + result.rows.length + ', skipped ' + result.skipped);
    return json_({ ok: true, appended: result.rows.length, skipped: result.skipped });
  } finally {
    lock.releaseLock();
  }
}

function handleAppendSales_(body) {
  if (!body.header || !body.rows) return json_({ ok: false, error: 'Missing header/rows' });

  // Serialize concurrent pushes. Without a lock, two simultaneous appends both read the same last
  // row and either double-append a receipt or overwrite each other's rows (spec: no double-counting,
  // nothing lost). Apps Script runs web-app requests in parallel, so this is required.
  var lock = LockService.getDocumentLock();
  try {
    lock.waitLock(30000);
  } catch (e) {
    return json_({ ok: false, error: 'Sheet busy, please retry' });
  }
  try {
    var sheet = getOrCreateSalesSheet_(body.header);
    var receiptCol = body.header.indexOf('receipt'); // 0-based dedup-key column
    if (receiptCol < 0) receiptCol = 0;
    var lastRow = sheet.getLastRow();
    var existing = [];
    if (lastRow > 1) {
      existing = sheet.getRange(2, receiptCol + 1, lastRow - 1, 1)
        .getValues().map(function (r) { return String(r[0]); });
    }
    var result = filterNewRows(body.rows, existing, receiptCol);
    if (result.rows.length > 0) {
      var startRow = sheet.getLastRow() + 1;
      // Force identifier columns to plain text so Sheets can't coerce them on write (e.g. a receipt
      // like "07-1" -> a date serial), which would change the stored value and break the dedup
      // round-trip. Numeric columns (prices, qty) are left to coerce to numbers as intended.
      forceTextColumn_(sheet, startRow, result.rows.length, receiptCol);
      forceTextColumn_(sheet, startRow, result.rows.length, body.header.indexOf('accession'));
      sheet.getRange(startRow, 1, result.rows.length, body.header.length).setValues(result.rows);
      // Stamp ONLY these freshly appended rows "Pending" so the Access reverse sync (selectPendingSales)
      // picks them up. The app's payload header has no sync_status column, so the column lives only on
      // the sheet (added manually to the Sales tab). Skipped/duplicate rows are left as-is — never
      // re-stamped, so an already-Synced row can't be resurrected to Pending.
      stampPending_(sheet, startRow, result.rows.length);
    }
    recordSync_('Sales from device', 'device → Sheet',
      'appended ' + result.rows.length + ', skipped ' + result.skipped);
    return json_({ ok: true, appended: result.rows.length, skipped: result.skipped });
  } finally {
    lock.releaseLock();
  }
}

/**
 * Reverse sync, step 1 (Access pulls): return every "Sales" row whose sync_status is "Pending", shaped
 * as {receipt, item_seq, accession, qty, unit}. Read under the document lock so the list can't be taken
 * mid-write of an appendSales. Unpaged — a nursery day's volume is tiny. The error-prone selection lives
 * in shared.js selectPendingSales (unit-tested); this handler is thin Sheet glue.
 */
function handlePendingSales_() {
  var lock = LockService.getDocumentLock();
  try {
    lock.waitLock(30000);
  } catch (e) {
    return json_({ ok: false, error: 'Sheet busy, please retry' });
  }
  try {
    var sheet = SpreadsheetApp.getActive().getSheetByName('Sales');
    if (!sheet) return json_({ ok: false, error: 'No "Sales" sheet found' });
    var values = sheet.getDataRange().getValues();
    var sales = selectPendingSales(values);
    recordSync_('Sales to Access', 'Sheet → Access', sales.length + ' pending');
    return json_({ ok: true, sales: sales, count: sales.length });
  } finally {
    lock.releaseLock();
  }
}

/**
 * Reverse sync, step 2 (Access acknowledges): set each addressed row's sync_status to the status it is
 * handed. Body: { keys: [{receipt, item_seq, status}] }. Located by the (receipt, item_seq) primary key;
 * keys absent from the sheet are ignored without error. Status-agnostic by design — Access decides the
 * status ("Synced", or "NoMatch" in a later slice) and this just writes it. Written under the document
 * lock. The key resolution lives in shared.js resolveSalesMarks (unit-tested).
 */
function handleMarkSalesSynced_(body) {
  if (!body.keys) return json_({ ok: false, error: 'Missing keys' });

  var lock = LockService.getDocumentLock();
  try {
    lock.waitLock(30000);
  } catch (e) {
    return json_({ ok: false, error: 'Sheet busy, please retry' });
  }
  try {
    var sheet = SpreadsheetApp.getActive().getSheetByName('Sales');
    if (!sheet) return json_({ ok: false, error: 'No "Sales" sheet found' });
    var values = sheet.getDataRange().getValues();
    var statusCol = salesColIndex(values[0], 'sync_status');
    if (statusCol < 0) return json_({ ok: false, error: 'No sync_status column' });

    var marks = resolveSalesMarks(values, body.keys);
    marks.forEach(function (m) {
      sheet.getRange(m.rowIndex + 1, statusCol + 1).setValue(m.status);
    });
    recordSync_('Sales to Access', 'Sheet → Access', 'marked ' + marks.length);
    return json_({ ok: true, marked: marks.length });
  } finally {
    lock.releaseLock();
  }
}

/**
 * Reverse sync, step 1 (Access pulls culls): return every "Culls" row whose sync_status is "Pending",
 * shaped as {cull_id, accession, qty, unit, notes}. Read under the document lock. The error-prone
 * selection lives in shared.js selectPendingCulls (unit-tested).
 */
function handlePendingCulls_() {
  var lock = LockService.getDocumentLock();
  try {
    lock.waitLock(30000);
  } catch (e) {
    return json_({ ok: false, error: 'Sheet busy, please retry' });
  }
  try {
    var sheet = SpreadsheetApp.getActive().getSheetByName('Culls');
    if (!sheet) return json_({ ok: false, error: 'No "Culls" sheet found' });
    var values = sheet.getDataRange().getValues();
    var culls = selectPendingCulls(values);
    recordSync_('Culls to Access', 'Sheet → Access', culls.length + ' pending');
    return json_({ ok: true, culls: culls, count: culls.length });
  } finally {
    lock.releaseLock();
  }
}

/**
 * Reverse sync, step 2 (Access acknowledges culls): set each addressed row's sync_status to the status
 * it is handed. Body: { keys: [{cull_id, status}] }. Located by cull_id primary key; keys absent from
 * the sheet are ignored without error. Status-agnostic — Access decides ("Synced", "NoMatch",
 * "StockPlant", …). Key resolution lives in shared.js resolveCullMarks (unit-tested).
 */
function handleMarkCullsSynced_(body) {
  if (!body.keys) return json_({ ok: false, error: 'Missing keys' });

  var lock = LockService.getDocumentLock();
  try {
    lock.waitLock(30000);
  } catch (e) {
    return json_({ ok: false, error: 'Sheet busy, please retry' });
  }
  try {
    var sheet = SpreadsheetApp.getActive().getSheetByName('Culls');
    if (!sheet) return json_({ ok: false, error: 'No "Culls" sheet found' });
    var values = sheet.getDataRange().getValues();
    var statusCol = salesColIndex(values[0], 'sync_status');
    if (statusCol < 0) return json_({ ok: false, error: 'No sync_status column' });

    var marks = resolveCullMarks(values, body.keys);
    marks.forEach(function (m) {
      sheet.getRange(m.rowIndex + 1, statusCol + 1).setValue(m.status);
    });
    recordSync_('Culls to Access', 'Sheet → Access', 'marked ' + marks.length);
    return json_({ ok: true, marked: marks.length });
  } finally {
    lock.releaseLock();
  }
}

/** Set a single 0-based column of an append range to plain-text format (no-op if the column is absent). */
function forceTextColumn_(sheet, startRow, numRows, colIndex) {
  if (colIndex < 0) return;
  sheet.getRange(startRow, colIndex + 1, numRows, 1).setNumberFormat('@');
}

function getOrCreateSalesSheet_(header) {
  var ss = SpreadsheetApp.getActive();
  var sheet = ss.getSheetByName('Sales');
  var fullHeader = ensureSyncStatusColumn(header);
  if (!sheet) {
    sheet = ss.insertSheet('Sales');
    sheet.getRange(1, 1, 1, fullHeader.length).setValues([fullHeader]);
  } else if (sheet.getLastRow() === 0) {
    sheet.getRange(1, 1, 1, fullHeader.length).setValues([fullHeader]);
  } else {
    provisionSyncStatusColumn_(sheet);
  }
  return sheet;
}

function getOrCreateCullsSheet_(header) {
  var ss = SpreadsheetApp.getActive();
  var sheet = ss.getSheetByName('Culls');
  var fullHeader = ensureSyncStatusColumn(header);
  if (!sheet) {
    sheet = ss.insertSheet('Culls');
    sheet.getRange(1, 1, 1, fullHeader.length).setValues([fullHeader]);
  } else if (sheet.getLastRow() === 0) {
    sheet.getRange(1, 1, 1, fullHeader.length).setValues([fullHeader]);
  } else {
    provisionSyncStatusColumn_(sheet);
  }
  return sheet;
}

/** Append sync_status as the last header cell when an existing tab is missing it. */
function provisionSyncStatusColumn_(sheet) {
  var lastCol = sheet.getLastColumn();
  if (lastCol < 1) return;
  var headerRow = sheet.getRange(1, 1, 1, lastCol).getValues()[0];
  var ensured = ensureSyncStatusColumn(headerRow);
  if (ensured.length > headerRow.length) {
    sheet.getRange(1, ensured.length).setValue('sync_status');
  }
}

/**
 * Stamp `numRows` rows starting at `startRow` with sync_status "Pending" — the marker the Access reverse
 * sync selects on. The sync_status column is located by name from the sheet's OWN header (it isn't in the
 * app's payload header); auto-provisioned on first append when missing.
 */
function stampPending_(sheet, startRow, numRows) {
  var headerRow = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
  var statusCol = salesColIndex(headerRow, 'sync_status'); // 0-based; column is added manually
  if (statusCol < 0) return;                               // no sync_status column -> nothing to stamp
  var pending = [];
  for (var i = 0; i < numRows; i++) pending.push(['Pending']);
  sheet.getRange(startRow, statusCol + 1, numRows, 1).setValues(pending);
}

/**
 * Upsert one row in the "SyncStatus" sheet for `event`, stamping the current time and a short detail.
 * One row per event label, so the sheet always shows the LAST time each sync ran. Wrapped in try/catch
 * so a logging hiccup can never fail the actual sync. Callers inside handleReplacePlants_/
 * handleAppendSales_ already hold the document lock; handleGetPlants_ calls it lock-free (rare, benign).
 */
function recordSync_(event, direction, detail) {
  try {
    var ss = SpreadsheetApp.getActive();
    var sheet = ss.getSheetByName('SyncStatus');
    if (!sheet) {
      sheet = ss.insertSheet('SyncStatus');
      sheet.getRange(1, 1, 1, 4).setValues([['Event', 'Direction', 'Last Sync', 'Detail']]);
      sheet.setFrozenRows(1);
    }
    var lastRow = sheet.getLastRow();
    var keys = lastRow > 1 ? sheet.getRange(2, 1, lastRow - 1, 1).getValues() : [];
    var idx = findRowByKey(keys, event);
    var rowNum = idx >= 0 ? idx + 2 : (lastRow < 1 ? 2 : lastRow + 1); // +2: skip header + 0-based->1-based
    sheet.getRange(rowNum, 1, 1, 4).setValues([[event, direction, new Date(), detail]]);
  } catch (e) {
    // Logging must never break a sync.
  }
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
