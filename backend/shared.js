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
 * Index of the accession/barcode column in a header row, matched case-insensitively against the app
 * header name ('accession') and the raw Access header ('ac number'), or -1 if neither is present.
 * The accession IS the barcode; this is the single place that knows the column's possible names, so
 * parsePlants, planPlantReplace and the Code.gs replace handler all resolve it the same way.
 */
function accessionColIndex(header) {
  var lower = (header || []).map(function (h) { return String(h).trim().toLowerCase(); });
  var i = lower.indexOf('accession');
  if (i < 0) i = lower.indexOf('ac number');
  return i;
}

/**
 * Convert a 2D sheet range (row 0 = header) into plant objects for the app's getPlants response.
 *
 * The "Plants" sheet is now the raw Batches+Species view exported from Access, so columns are found
 * by their raw Access names (matched case-insensitively): accession <- "Ac Number", group <- "Plant
 * Type", light <- "Sun/Shade", and name is composed from "Genus" + "Species" + "Cultivar" (falling
 * back to "Common Name"). The legacy header names (accession/name/group/light) are still honoured if
 * present, so an older sheet keeps working. The accession IS the barcode; rows with a blank accession
 * are skipped.
 */
function parsePlants(values) {
  if (!values || values.length === 0) return [];
  var header = values[0].map(function (h) { return String(h).trim().toLowerCase(); });
  function col() {
    for (var i = 0; i < arguments.length; i++) {
      var j = header.indexOf(arguments[i]);
      if (j >= 0) return j;
    }
    return -1;
  }
  var iAcc = accessionColIndex(values[0]);
  var iName = col('name');
  var iGenus = col('genus'), iSpecies = col('species'),
      iCultivar = col('cultivar'), iCommon = col('common name');
  var iGroup = col('group', 'plant type');
  var iLight = col('light', 'sun/shade');
  var iPots = col('potsinnursery');
  var iTubes = col('tubesinnursery');
  var iMisc = col('miscinnursery');
  var iStock = col('stockinnursery');

  function get(row, idx) { return idx >= 0 ? String(row[idx]).trim() : ''; }
  function num(row, idx) {
    if (idx < 0) return 0;
    var v = row[idx];
    if (v === '' || v === null || v === undefined) return 0;
    var n = Number(v);
    return isNaN(n) ? 0 : n;
  }
  function nameOf(row) {
    var legacy = get(row, iName);
    if (legacy) return legacy;
    var base = [get(row, iGenus), get(row, iSpecies)].filter(Boolean).join(' ');
    var cv = get(row, iCultivar);
    if (cv) base = (base + " '" + cv + "'").trim();
    return base || get(row, iCommon);
  }

  var out = [];
  for (var r = 1; r < values.length; r++) {
    var row = values[r];
    var accession = iAcc >= 0 ? String(row[iAcc]).trim() : '';
    if (!accession) continue;
    out.push({
      accession: accession,
      name: nameOf(row),
      group: iGroup >= 0 ? emptyToNull(row[iGroup]) : null,
      light: iLight >= 0 ? emptyToNull(row[iLight]) : null,
      potsInNursery: num(row, iPots),
      tubesInNursery: num(row, iTubes),
      miscInNursery: num(row, iMisc),
      stockInNursery: num(row, iStock)
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

/**
 * Prepare rows for a FULL-MIRROR replace of the "Plants" sheet. Access is the system of record and
 * fully owns this sheet, and the sync is gated on stock > 0, so every run rewrites the sheet to the
 * current in-stock set: a plant that has sold out is simply absent from `rows` and therefore drops
 * off the sheet (no upsert, no stale stock). The caller clears the sheet and writes `header` + the
 * returned `rows`.
 *
 * Cleans the incoming batch: rows with a blank accession are skipped, and a duplicate accession
 * keeps the LAST occurrence (defensive — accession is unique in the source). Row/column order is the
 * order Access sent; the backend is column-agnostic and writes whatever header it is given.
 *
 * @param header column names (e.g. ['accession','name','group','light','potsInNursery',...]).
 * @param rows   rows aligned to `header`.
 * @returns { header, rows } ready to write.
 */
function planPlantReplace(header, rows) {
  header = header || [];
  rows = rows || [];
  var keyCol = accessionColIndex(header);
  if (keyCol < 0) keyCol = 0;

  var order = [];
  // Prototype-less map: accessions are free text, so an accession equal to a built-in prototype key
  // ('__proto__', 'constructor', 'toString', ...) must NOT look "already present" via inheritance.
  var byKey = Object.create(null);
  rows.forEach(function (row) {
    var raw = row[keyCol];
    var key = String(raw === undefined || raw === null ? '' : raw).trim();
    if (!key) return; // skip blank accession
    if (byKey[key] === undefined) order.push(key);
    byKey[key] = row;
  });

  return { header: header, rows: order.map(function (k) { return byKey[k]; }) };
}

/**
 * Index of the first row in `rows` whose first cell equals `key` (data rows only, no header), or -1.
 * Used by the SyncStatus log to upsert one row per sync event keyed on the event label.
 */
function findRowByKey(rows, key) {
  rows = rows || [];
  for (var i = 0; i < rows.length; i++) {
    if (String(rows[i][0]) === String(key)) return i;
  }
  return -1;
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { isAuthorized, emptyToNull, parsePlants, filterNewRows, planPlantReplace, findRowByKey, accessionColIndex };
}
