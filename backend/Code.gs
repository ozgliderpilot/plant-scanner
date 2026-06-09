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
  var sheet = getOrCreateSalesSheet_(body.header);
  var lastRow = sheet.getLastRow();
  var existing = [];
  if (lastRow > 1) {
    existing = sheet.getRange(2, 1, lastRow - 1, 1).getValues().map(function (r) { return String(r[0]); });
  }
  var result = filterNewRows(body.rows, existing, 0);
  if (result.rows.length > 0) {
    sheet.getRange(sheet.getLastRow() + 1, 1, result.rows.length, body.header.length).setValues(result.rows);
  }
  return json_({ ok: true, appended: result.rows.length, skipped: result.skipped });
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
