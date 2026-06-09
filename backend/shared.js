/**
 * Pure logic shared by the Apps Script web app and the Node tests.
 *
 * In Google Apps Script: paste this file's contents as a script file named `shared.gs`. GAS
 * concatenates all .gs files, so `Code.gs` can call these functions directly. The `module.exports`
 * guard at the bottom is inert in GAS (there is no `module`) and lets Node `require()` it for tests.
 */

/** True when the request carries the configured shared secret. */
function isAuthorized(body, expectedSecret) {
  return !!expectedSecret && !!body && body.secret === expectedSecret;
}

function emptyToNull(v) {
  if (v === undefined || v === null) return null;
  var s = String(v).trim();
  return s === '' ? null : s;
}

/**
 * Convert a 2D sheet range (row 0 = header) into plant objects. Column order is driven by the
 * header names (accession, name, group, light), so columns can be reordered safely. The accession
 * IS the barcode value, so there is no separate barcode column. Rows with a blank accession are skipped.
 */
function parsePlants(values) {
  if (!values || values.length === 0) return [];
  var header = values[0].map(function (h) { return String(h).trim().toLowerCase(); });
  var idx = {
    accession: header.indexOf('accession'),
    name: header.indexOf('name'),
    group: header.indexOf('group'),
    light: header.indexOf('light')
  };
  var out = [];
  for (var r = 1; r < values.length; r++) {
    var row = values[r];
    var accession = idx.accession >= 0 ? String(row[idx.accession]).trim() : '';
    if (!accession) continue;
    out.push({
      accession: accession,
      name: idx.name >= 0 ? String(row[idx.name]).trim() : '',
      group: idx.group >= 0 ? emptyToNull(row[idx.group]) : null,
      light: idx.light >= 0 ? emptyToNull(row[idx.light]) : null
    });
  }
  return out;
}

/**
 * Append-safety net against double counting (spec). Skip every incoming row whose receipt number
 * already exists in the sheet; keep the rest. NOTE: it dedupes only against the EXISTING sheet, so
 * the multiple line-rows of one new receipt are all kept.
 */
function filterNewRows(rows, existingReceiptNos, receiptColIndex) {
  var existing = {};
  (existingReceiptNos || []).forEach(function (n) { existing[String(n)] = true; });
  var toAppend = [];
  var skipped = 0;
  (rows || []).forEach(function (row) {
    var receiptNo = String(row[receiptColIndex]);
    if (existing[receiptNo]) { skipped++; } else { toAppend.push(row); }
  });
  return { rows: toAppend, skipped: skipped };
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { isAuthorized, emptyToNull, parsePlants, filterNewRows };
}
