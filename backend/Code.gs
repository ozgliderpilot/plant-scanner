/**
 * Google Apps Script Web App for the Nursery app. POST actions, all authorised by a shared
 * secret stored in Script Properties (key SHARED_SECRET):
 *   - getPlants       -> reads the "Plants" sheet, returns plant objects (the manual "Update plant list")
 *   - replacePlants   -> full-mirror rewrite of the "Plants" sheet from Access (in-stock plants only)
 *   - appendSales     -> appends sales rows to "Sales" (deduped by receipt #), stamping each newly
 *                        appended row's sync_status "Pending" for the Access reverse sync (auto-export/push),
 *                        and predicts Plants-tab stock deductions (#80)
 *   - appendCulls     -> appends cull rows to "Culls" (deduped by cull_id), stamping sync_status,
 *                        and predicts Plants-tab stock deductions (#80)
 *   - appendPrintLabels -> appends label print rows to "PrintQueue" (deduped by queue_id), stamping sync_status
 *   - pendingSales    -> returns every "Sales" row whose sync_status is "Pending" (Access reverse sync)
 *   - markSalesSynced -> sets sync_status by (receipt,item_seq) key (Access reverse sync, status-agnostic)
 *   - pendingCulls    -> returns every "Culls" row whose sync_status is "Pending" (Access reverse sync)
 *   - markCullsSynced -> sets sync_status by cull_id key (Access reverse sync, status-agnostic)
 *   - pendingPrintLabels -> returns every "PrintQueue" row whose sync_status is "Pending" (Access reverse sync)
 *   - markPrintLabelsSynced -> sets sync_status by queue_id key (Access reverse sync, status-agnostic)
 *
 * Every action also stamps a "SyncStatus" sheet (rolling log of the last 100 sync events, newest
 * first) so the Sheet shows recent plant pushes from Access and pulls / pushes with the device.
 *
 * Plant-list fingerprints for conditional getPlants are cached in Script Properties
 * (key PLANT_LIST_FINGERPRINT) — see ADR-0016.
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
      case 'getPlants': return handleGetPlants_(body);
      case 'replacePlants': return handleReplacePlants_(body);
      case 'appendSales': return handleAppendSales_(body);
      case 'appendCulls': return handleAppendCulls_(body);
      case 'appendPrintLabels': return handleAppendPrintLabels_(body);
      case 'pendingSales': return handlePendingSales_();
      case 'markSalesSynced': return handleMarkSalesSynced_(body);
      case 'pendingCulls': return handlePendingCulls_();
      case 'markCullsSynced': return handleMarkCullsSynced_(body);
      case 'pendingPrintLabels': return handlePendingPrintLabels_();
      case 'markPrintLabelsSynced': return handleMarkPrintLabelsSynced_(body);
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

var PLANT_LIST_FINGERPRINT_KEY_ = 'PLANT_LIST_FINGERPRINT';

function getCachedPlantListFingerprint_() {
  return PropertiesService.getScriptProperties().getProperty(PLANT_LIST_FINGERPRINT_KEY_);
}

function setCachedPlantListFingerprint_(fingerprint) {
  PropertiesService.getScriptProperties().setProperty(PLANT_LIST_FINGERPRINT_KEY_, String(fingerprint || ''));
}

/** Recompute and store the plant-list fingerprint from the current Plants sheet. */
function refreshPlantListFingerprint_() {
  var sheet = SpreadsheetApp.getActive().getSheetByName('Plants');
  if (!sheet) {
    setCachedPlantListFingerprint_('');
    return '';
  }
  var plants = parsePlants(sheet.getDataRange().getValues());
  var fp = computePlantListFingerprint(plants);
  setCachedPlantListFingerprint_(fp);
  return fp;
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

function handleGetPlants_(body) {
  return withDocumentLock_(function () {
    var cached = getCachedPlantListFingerprint_();
    if (plantListFingerprintMatches(body && body.plantListFingerprint, cached)) {
      return json_({
        ok: true,
        unchanged: true,
        plantListFingerprint: String(cached).trim(),
      });
    }

    var sheet = SpreadsheetApp.getActive().getSheetByName('Plants');
    if (!sheet) return json_({ ok: false, error: 'No "Plants" sheet found' });
    var values = sheet.getDataRange().getValues();
    var plants = parsePlants(values);
    var fingerprint = computePlantListFingerprint(plants);
    setCachedPlantListFingerprint_(fingerprint);
    recordSync_('Plant list to device', 'Sheet → device', plants.length + ' plants');
    return json_({
      ok: true,
      plants: plants,
      count: plants.length,
      plantListFingerprint: fingerprint,
      updatedAt: new Date().toISOString(),
    });
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

    var fingerprint = computePlantListFingerprint(parsePlants(out));
    setCachedPlantListFingerprint_(fingerprint);

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
    predictKind: 'culls',
  });
}

function handleAppendPrintLabels_(body) {
  return handleAppendExport_(body, {
    sheetName: 'PrintQueue',
    keyColumn: 'queue_id',
    syncEvent: 'Print labels from device',
    validate: validateAppendPrintLabelCopies,
  });
}

function handleAppendSales_(body) {
  return handleAppendExport_(body, {
    sheetName: 'Sales',
    keyColumn: 'receipt',
    syncEvent: 'Sales from device',
    predictKind: 'sales',
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
      if (opts.predictKind) {
        // Best-effort (#80): prediction failure must not fail or roll back the append.
        try {
          predictPlantsStock_(body.header, result.rows, opts.predictKind);
        } catch (e) {
          // Access replacePlants will self-correct on the next reverse sync.
          console.error('predictPlantsStock_ failed after append:', e);
        }
      }
    }
    recordSync_(opts.syncEvent, 'device → Sheet',
      'appended ' + result.rows.length + ', skipped ' + result.skipped);
    return json_({ ok: true, appended: result.rows.length, skipped: result.skipped });
  });
}

/**
 * Apply predicted Pots/Tubes/Misc stock deductions to the Plants tab for newly appended rows.
 * Caller holds the document lock. Swallows nothing — callers wrap in try/catch.
 * Columns are not always contiguous on the Access mirror, so each stock column is batch-written
 * separately (same contiguous-run pattern as applyExportMarks_).
 */
function predictPlantsStock_(exportHeader, appendedRows, kind) {
  var plantsSheet = SpreadsheetApp.getActive().getSheetByName('Plants');
  if (!plantsSheet || plantsSheet.getLastRow() < 2) return;
  var values = plantsSheet.getDataRange().getValues();
  var updates = predictStockUpdates(values, exportHeader, appendedRows, kind);
  if (!updates.length) return;

  var header = values[0];
  var iPots = headerColIndex(header, 'potsinnursery');
  var iTubes = headerColIndex(header, 'tubesinnursery');
  var iMisc = headerColIndex(header, 'miscinnursery');
  if (iPots < 0 || iTubes < 0 || iMisc < 0) return;

  // Refresh even if a later column write throws: otherwise Script Properties can
  // stay on the pre-mutation fingerprint and getPlants may answer unchanged
  // while Plants already reflects a partial prediction (ADR-0016).
  try {
    writePredictedStockColumn_(plantsSheet, updates, iPots, 'pots');
    writePredictedStockColumn_(plantsSheet, updates, iTubes, 'tubes');
    writePredictedStockColumn_(plantsSheet, updates, iMisc, 'misc');
  } finally {
    refreshPlantListFingerprint_();
  }
}

/** Batch-write one stock column for sorted updates, coalescing contiguous plant rows. */
function writePredictedStockColumn_(sheet, updates, colIndex, field) {
  var i = 0;
  while (i < updates.length) {
    var startRow = updates[i].rowIndex + 1; // sheet is 1-based; values rowIndex is 0-based
    var block = [[updates[i][field]]];
    i++;
    while (i < updates.length && updates[i].rowIndex === updates[i - 1].rowIndex + 1) {
      block.push([updates[i][field]]);
      i++;
    }
    sheet.getRange(startRow, colIndex + 1, block.length, 1).setValues(block);
  }
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

function handlePendingPrintLabels_() {
  return handlePendingExport_('PrintQueue', selectPendingPrintLabels, 'printLabels', 'Print labels to Access');
}

function handleMarkPrintLabelsSynced_(body) {
  return handleMarkExportSynced_(body, 'PrintQueue', resolvePrintLabelMarks, 'Print labels to Access');
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
 * Append one history row to the "SyncStatus" sheet (newest first), keeping at most
 * SYNC_STATUS_MAX_ROWS data rows. Existing rows are preserved as history — never wiped.
 * Wrapped in try/catch so a logging hiccup can never fail the actual sync. Callers inside
 * handleReplacePlants_/handleAppendSales_ already hold the document lock; handleGetPlants_
 * calls it lock-free (rare, benign).
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
    var existing = lastRow > 1
      ? sheet.getRange(2, 1, lastRow - 1, 4).getValues()
      : [];
    var planned = planSyncStatusLog(existing, [event, direction, new Date(), detail]);
    if (planned.length > 0) {
      sheet.getRange(2, 1, planned.length, 4).setValues(planned);
    }
    // Drop any rows past the planned length (header + planned).
    var excess = sheet.getLastRow() - (1 + planned.length);
    if (excess > 0) {
      sheet.deleteRows(2 + planned.length, excess);
    }
  } catch (e) {
    console.error(e);
  }
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
