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

function withDocumentLock_(fn) {
  var lock = LockService.getDocumentLock();
  try {
    lock.waitLock(30000);
  } catch (e) {
    return json_({ ok: false, error: 'Sheet busy, please retry' });
  }
  try {
    return fn();
  } finally {
    lock.releaseLock();
  }
}

function handleGetPlants_() {
  return withDocumentLock_(function () {
    var sheet = SpreadsheetApp.getActive().getSheetByName('Plants');
    if (!sheet) return json_({ ok: false, error: 'No "Plants" sheet found' });
    var values = sheet.getDataRange().getValues();
    var plants = parsePlants(values);
    recordSync_('Plant list to device', 'Sheet → device', plants.length + ' plants');
    return json_({ ok: true, plants: plants, count: plants.length, updatedAt: new Date().toISOString() });
  });
}

function handleReplacePlants_(body) {
  if (!body.header || !body.rows) return json_({ ok: false, error: 'Missing header/rows' });

  return withDocumentLock_(function () {
    var ss = SpreadsheetApp.getActive();
    var sheet = ss.getSheetByName('Plants');
    if (!sheet) sheet = ss.insertSheet('Plants');

    var plan = planPlantReplace(body.header, body.rows);
    if (plan.header.length === 0) return json_({ ok: false, error: 'Empty header' });
    var accCol = accessionColIndex(plan.header);
    if (accCol < 0) accCol = 0;

    var out = [plan.header].concat(plan.rows);

    sheet.clearContents();
    if (plan.rows.length > 0) forceTextColumn_(sheet, 2, plan.rows.length, accCol);
    sheet.getRange(1, 1, out.length, plan.header.length).setValues(out);

    recordSync_('Plants from Access', 'Access → Sheet', plan.rows.length + ' plants');
    return json_({ ok: true, written: plan.rows.length });
  });
}

function handleAppendCulls_(body) {
  return handleAppendExport_(body, {
    sheetName: 'Culls',
    keyColumn: 'cull_id',
    syncEvent: 'Culls from device',
    validate: validateAppendCullsNotes,
  });
}

function handleAppendSales_(body) {
  return handleAppendExport_(body, {
    sheetName: 'Sales',
    keyColumn: 'receipt',
    syncEvent: 'Sales from device',
  });
}

function handleAppendExport_(body, opts) {
  if (!body.header || !body.rows) return json_({ ok: false, error: 'Missing header/rows' });
  if (opts.validate) {
    var notesError = opts.validate(body.header, body.rows);
    if (notesError) return json_({ ok: false, error: notesError });
  }

  return withDocumentLock_(function () {
    var sheet = getOrCreateExportSheet_(opts.sheetName, body.header);
    var keyCol = salesColIndex(body.header, opts.keyColumn);
    if (keyCol < 0) keyCol = 0;
    var accCol = salesColIndex(body.header, 'accession');
    var lastRow = sheet.getLastRow();
    var existing = [];
    if (lastRow > 1) {
      existing = sheet.getRange(2, keyCol + 1, lastRow - 1, 1)
        .getValues().map(function (r) { return String(r[0]); });
    }
    var result = filterNewRows(body.rows, existing, keyCol);
    if (result.rows.length > 0) {
      var startRow = lastRow + 1;
      forceTextColumn_(sheet, startRow, result.rows.length, keyCol);
      forceTextColumn_(sheet, startRow, result.rows.length, accCol);
      sheet.getRange(startRow, 1, result.rows.length, body.header.length).setValues(result.rows);
      var statusCol = salesColIndex(sheetHeaderRow_(sheet), 'sync_status');
      stampPending_(sheet, startRow, result.rows.length, statusCol);
    }
    recordSync_(opts.syncEvent, 'device → Sheet',
      'appended ' + result.rows.length + ', skipped ' + result.skipped);
    return json_({ ok: true, appended: result.rows.length, skipped: result.skipped });
  });
}

function handlePendingSales_() {
  return handlePendingExport_('Sales', selectPendingSales, 'sales', 'Sales to Access');
}

function handleMarkSalesSynced_(body) {
  return handleMarkExportSynced_(body, 'Sales', resolveSalesMarks, 'Sales to Access');
}

function handlePendingCulls_() {
  return handlePendingExport_('Culls', selectPendingCulls, 'culls', 'Culls to Access');
}

function handleMarkCullsSynced_(body) {
  return handleMarkExportSynced_(body, 'Culls', resolveCullMarks, 'Culls to Access');
}

function handlePendingExport_(sheetName, selectFn, resultKey, syncEvent) {
  return withDocumentLock_(function () {
    var sheet = SpreadsheetApp.getActive().getSheetByName(sheetName);
    if (!sheet) return json_({ ok: false, error: 'No "' + sheetName + '" sheet found' });
    var values = sheet.getDataRange().getValues();
    var items = selectFn(values);
    recordSync_(syncEvent, 'Sheet → Access', items.length + ' pending');
    var resp = { ok: true, count: items.length };
    resp[resultKey] = items;
    return json_(resp);
  });
}

function handleMarkExportSynced_(body, sheetName, resolveFn, syncEvent) {
  if (!body.keys) return json_({ ok: false, error: 'Missing keys' });

  return withDocumentLock_(function () {
    var sheet = SpreadsheetApp.getActive().getSheetByName(sheetName);
    if (!sheet) return json_({ ok: false, error: 'No "' + sheetName + '" sheet found' });
    var values = sheet.getDataRange().getValues();
    var statusCol = salesColIndex(values[0], 'sync_status');
    if (statusCol < 0) return json_({ ok: false, error: 'No sync_status column' });

    var marks = resolveFn(values, body.keys);
    applyExportMarks_(sheet, values, marks, statusCol);
    recordSync_(syncEvent, 'Sheet → Access', 'marked ' + marks.length);
    return json_({ ok: true, marked: marks.length });
  });
}

/** Batch-write sync_status updates and optional plant-field enrichment for unknown rows. */
function applyExportMarks_(sheet, values, marks, statusCol) {
  if (!marks.length) return;
  var header = values[0];
  var iName = headerColIndex(header, 'name');
  var colByField = {};
  PLANT_ENRICHMENT_FIELDS.forEach(function (f) {
    colByField[f] = headerColIndex(header, f);
  });

  marks.sort(function (a, b) { return a.rowIndex - b.rowIndex; });
  var i = 0;
  while (i < marks.length) {
    var startRow = marks[i].rowIndex + 1;
    var statusValues = [[marks[i].status]];
    i++;
    while (i < marks.length && marks[i].rowIndex === marks[i - 1].rowIndex + 1) {
      statusValues.push([marks[i].status]);
      i++;
    }
    sheet.getRange(startRow, statusCol + 1, statusValues.length, 1).setValues(statusValues);
  }

  marks.forEach(function (mark) {
    if (!mark.enrichment || iName < 0 || !isUnknownPlantName(values[mark.rowIndex][iName])) return;
    PLANT_ENRICHMENT_FIELDS.forEach(function (f) {
      var col = colByField[f];
      if (col >= 0 && mark.enrichment[f] !== undefined) {
        sheet.getRange(mark.rowIndex + 1, col + 1).setValue(mark.enrichment[f]);
      }
    });
  });
}

/** Set a single 0-based column of an append range to plain-text format (no-op if the column is absent). */
function forceTextColumn_(sheet, startRow, numRows, colIndex) {
  if (colIndex < 0) return;
  sheet.getRange(startRow, colIndex + 1, numRows, 1).setNumberFormat('@');
}

function sheetHeaderRow_(sheet) {
  var lastCol = sheet.getLastColumn();
  return lastCol < 1 ? [] : sheet.getRange(1, 1, 1, lastCol).getValues()[0];
}

function getOrCreateExportSheet_(name, header) {
  var ss = SpreadsheetApp.getActive();
  var sheet = ss.getSheetByName(name);
  var fullHeader = ensureSyncStatusColumn(header);
  if (!sheet) {
    sheet = ss.insertSheet(name);
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

function stampPending_(sheet, startRow, numRows, statusCol) {
  if (statusCol < 0) return;
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
    console.error(e);
  }
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
