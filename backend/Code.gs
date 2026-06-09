/**
 * Google Apps Script Web App for the Nursery app. Two POST actions, both authorised by a shared
 * secret stored in Script Properties (key SHARED_SECRET):
 *   - getPlants   -> reads the "Plants" sheet, returns plant objects (the manual "Update plant list")
 *   - appendSales -> appends sales rows to the "Sales" sheet, deduped by receipt # (auto-export/push)
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
      case 'appendSales': return handleAppendSales_(body);
      default: return json_({ ok: false, error: 'Unknown action: ' + body.action });
    }
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  }
}

function doGet() {
  return json_({ ok: true, message: 'Nursery backend is running. Use POST.' });
}

function getSecret_() {
  return PropertiesService.getScriptProperties().getProperty('SHARED_SECRET');
}

function handleGetPlants_() {
  var sheet = SpreadsheetApp.getActive().getSheetByName('Plants');
  if (!sheet) return json_({ ok: false, error: 'No "Plants" sheet found' });
  var values = sheet.getDataRange().getValues();
  var plants = parsePlants(values);
  return json_({ ok: true, plants: plants, count: plants.length, updatedAt: new Date().toISOString() });
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
      // round-trip. Numeric columns (prices, pots) are left to coerce to numbers as intended.
      forceTextColumn_(sheet, startRow, result.rows.length, receiptCol);
      forceTextColumn_(sheet, startRow, result.rows.length, body.header.indexOf('accession'));
      sheet.getRange(startRow, 1, result.rows.length, body.header.length).setValues(result.rows);
    }
    return json_({ ok: true, appended: result.rows.length, skipped: result.skipped });
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
  if (!sheet) {
    sheet = ss.insertSheet('Sales');
    sheet.getRange(1, 1, 1, header.length).setValues([header]);
  } else if (sheet.getLastRow() === 0) {
    sheet.getRange(1, 1, 1, header.length).setValues([header]);
  }
  return sheet;
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
