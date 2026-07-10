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

function rowStr(row, idx) {
  return idx >= 0 ? String(row[idx]).trim() : '';
}

function rowNum(row, idx) {
  if (idx < 0) return 0;
  var n = Number(row[idx]);
  return isNaN(n) ? 0 : n;
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
 * Compose a display name from Species columns — same rules as parsePlants nameOf.
 */
function composePlantName(genus, species, cultivar, commonName) {
  var base = [String(genus || '').trim(), String(species || '').trim()].filter(Boolean).join(' ');
  var cv = String(cultivar || '').trim();
  if (cv) base = (base + " '" + cv + "'").trim();
  return base || String(commonName || '').trim();
}

/** True when a sheet row name is the app's unknown-scan sentinel (trimmed, case-insensitive). */
function isUnknownPlantName(name) {
  return String(name === undefined || name === null ? '' : name).trim().toLowerCase() === 'unknown';
}

/** Optional plant metadata fields carried in markSalesSynced / markCullsSynced keys. */
var PLANT_ENRICHMENT_FIELDS = ['name', 'genus', 'species', 'cultivar', 'common_name', 'group'];

/** Extract non-blank enrichment fields from a mark key object, or null when none are present. */
function pickPlantEnrichment(key) {
  if (!key) return null;
  var out = null;
  PLANT_ENRICHMENT_FIELDS.forEach(function (f) {
    if (key[f] === undefined || key[f] === null) return;
    var s = String(key[f]).trim();
    if (!s) return;
    if (!out) out = {};
    out[f] = s;
  });
  return out;
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

  function nameOf(row) {
    var legacy = rowStr(row, iName);
    if (legacy) return legacy;
    return composePlantName(rowStr(row, iGenus), rowStr(row, iSpecies),
      rowStr(row, iCultivar), rowStr(row, iCommon));
  }

  var out = [];
  for (var r = 1; r < values.length; r++) {
    var row = values[r];
    var accession = iAcc >= 0 ? String(row[iAcc]).trim() : '';
    if (!accession) continue;
    out.push({
      accession: accession,
      name: nameOf(row),
      genus: rowStr(row, iGenus),
      species: rowStr(row, iSpecies),
      cultivar: rowStr(row, iCultivar),
      commonName: rowStr(row, iCommon),
      group: iGroup >= 0 ? emptyToNull(row[iGroup]) : null,
      light: iLight >= 0 ? emptyToNull(row[iLight]) : null,
      potsInNursery: rowNum(row, iPots),
      tubesInNursery: rowNum(row, iTubes),
      miscInNursery: rowNum(row, iMisc),
      stockInNursery: rowNum(row, iStock)
    });
  }
  return out;
}

/**
 * Return a header row with a trailing sync_status column when absent. If sync_status already exists
 * anywhere in the row (any casing), the header is returned unchanged — never duplicated.
 */
function ensureSyncStatusColumn(header) {
  var h = (header || []).map(function (x) { return String(x).trim(); });
  var lower = h.map(function (x) { return x.toLowerCase(); });
  if (lower.indexOf('sync_status') >= 0) return h;
  return h.concat(['sync_status']);
}

/**
 * Append-safety net against double counting (spec). Skip every incoming row whose receipt number
 * already exists in the sheet; keep the rest. NOTE: it dedupes only against the EXISTING sheet, so
 * the multiple line-rows of one new receipt are all kept.
 */
function filterNewRows(rows, existingKeys, keyColIndex) {
  var existing = {};
  (existingKeys || []).forEach(function (n) { existing[String(n)] = true; });
  var toAppend = [];
  var skipped = 0;
  (rows || []).forEach(function (row) {
    var key = String(row[keyColIndex]);
    if (existing[key]) { skipped++; } else { toAppend.push(row); }
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

/** Index of a named column in a sheet header row (trimmed, case-insensitive), or -1 if absent. */
function headerColIndex(header, name) {
  var lower = (header || []).map(function (h) { return String(h).trim().toLowerCase(); });
  return lower.indexOf(name);
}

/** @deprecated Use headerColIndex — kept for existing call sites. */
function salesColIndex(header, name) { return headerColIndex(header, name); }

/**
 * Composite (receipt, item_seq) primary key for a Sales row, normalised so the two ends of the reverse
 * sync agree: receipt is a trimmed string, item_seq a canonical number (so 3, "3" and " 3 " collide).
 * A NUL joiner keeps "1" + "23" from colliding with "12" + "3".
 */
function salesRowKey(receipt, itemSeq) {
  var rcpt = String(receipt === undefined || receipt === null ? '' : receipt).trim();
  var n = Number(itemSeq);
  var seq = isNaN(n) ? String(itemSeq === undefined || itemSeq === null ? '' : itemSeq).trim() : String(n);
  return rcpt + '\u0000' + seq;
}

/**
 * Backing logic for the `pendingSales` action: select the reverse-sync work set from the raw "Sales"
 * sheet values (row 0 = header). Returns every row whose `sync_status` is exactly "Pending" (trimmed,
 * case-insensitive), shaped as the minimal object the Access sales-in phase consumes:
 * `{receipt, item_seq, accession, qty, unit, name}`. `name` lets Access skip Species lookup/enrichment
 * for rows that are already resolved (only `unknown` needs backfill).
 *
 * Columns are resolved by header name, so reordered / extra columns are tolerated and a missing data
 * column yields a default ('' for strings, 0 for numbers). A header-only or empty sheet, or one with no
 * `sync_status` column, yields []. Identifier columns (receipt, accession) are kept as trimmed strings;
 * `item_seq` and `qty` are parsed as numbers; the sale `unit` is a trimmed string.
 */
function selectPendingSales(values) {
  if (!values || values.length < 2) return [];
  var header = values[0];
  var iStatus = salesColIndex(header, 'sync_status');
  if (iStatus < 0) return [];
  var iReceipt = salesColIndex(header, 'receipt');
  var iSeq = salesColIndex(header, 'item_seq');
  var iAcc = salesColIndex(header, 'accession');
  var iQty = salesColIndex(header, 'qty');
  var iUnit = salesColIndex(header, 'unit');
  var iName = salesColIndex(header, 'name');

  var out = [];
  for (var r = 1; r < values.length; r++) {
    var row = values[r];
    if (String(row[iStatus]).trim().toLowerCase() !== 'pending') continue;
    out.push({
      receipt: rowStr(row, iReceipt),
      item_seq: rowNum(row, iSeq),
      accession: rowStr(row, iAcc),
      qty: rowNum(row, iQty),
      unit: rowStr(row, iUnit),
      name: rowStr(row, iName)
    });
  }
  return out;
}

/**
 * Backing logic for the `markSalesSynced` action: resolve a batch of mark requests against the raw
 * "Sales" sheet values (row 0 = header). Each request is `{receipt, item_seq, status}`; this finds the
 * data row whose `(receipt, item_seq)` primary key matches (see salesRowKey) and pairs it with the
 * requested status. The action is status-agnostic — it carries whatever status each request supplies.
 *
 * Returns `[{ rowIndex, status }]` where `rowIndex` is the 0-based index into `values` (so the header is
 * row 0 and the caller writes sheet row `rowIndex + 1`). A key with no matching row is silently ignored
 * (not an error) — a row may have been removed between the pendingSales read and the mark call.
 */
function resolveSalesMarks(values, keys) {
  if (!values || values.length < 2 || !keys || keys.length === 0) return [];
  var header = values[0];
  var iReceipt = salesColIndex(header, 'receipt');
  var iSeq = salesColIndex(header, 'item_seq');
  if (iReceipt < 0 || iSeq < 0) return [];

  // Prototype-less index of data rows by composite key; receipt/item_seq are free text, so a key equal
  // to a built-in prototype name ('__proto__', ...) must not appear "present" via inheritance.
  var byKey = Object.create(null);
  for (var r = 1; r < values.length; r++) {
    var k = salesRowKey(values[r][iReceipt], values[r][iSeq]);
    if (byKey[k] === undefined) byKey[k] = r; // first occurrence wins (the PK is unique)
  }

  var out = [];
  (keys || []).forEach(function (req) {
    var rowIndex = byKey[salesRowKey(req.receipt, req.item_seq)];
    if (rowIndex !== undefined) {
      var mark = { rowIndex: rowIndex, status: req.status };
      var enrichment = pickPlantEnrichment(req);
      if (enrichment) mark.enrichment = enrichment;
      out.push(mark);
    }
  });
  return out;
}

/**
 * Reject appendCulls payloads whose notes column contains [, ], {, or } — Access reverse-sync JSON
 * parsing uses simple delimiters and these break it. Returns an error message when invalid, null when
 * every row is OK. Resolves the notes column from header via salesColIndex.
 */
function validateAppendCullsNotes(header, rows) {
  var iNotes = salesColIndex(header, 'notes');
  if (iNotes < 0) return null;
  for (var r = 0; r < (rows || []).length; r++) {
    var row = rows[r];
    var notes = row && iNotes < row.length ? row[iNotes] : '';
    notes = String(notes === undefined || notes === null ? '' : notes);
    if (/[[\]{}]/.test(notes)) {
      return 'Cull notes contain unsupported characters';
    }
  }
  return null;
}

/**
 * Normalised cull_id primary key for a Culls row — trimmed string so " PP-1 " and "PP-1" collide.
 */
function cullRowKey(cullId) {
  return String(cullId === undefined || cullId === null ? '' : cullId).trim();
}

/**
 * Backing logic for the `pendingCulls` action: select the reverse-sync work set from the raw "Culls"
 * sheet values (row 0 = header). Returns every row whose `sync_status` is exactly "Pending" (trimmed,
 * case-insensitive), shaped as the minimal object the Access culls-in phase consumes:
 * `{cull_id, accession, qty, unit, notes, name}`. `name` lets Access skip Species lookup/enrichment
 * for rows that are already resolved (only `unknown` needs backfill).
 *
 * Columns are resolved by header name, so reordered / extra columns are tolerated and a missing data
 * column yields a default ('' for strings, 0 for numbers). A header-only or empty sheet, or one with no
 * `sync_status` column, yields [].
 */
function selectPendingCulls(values) {
  if (!values || values.length < 2) return [];
  var header = values[0];
  var iStatus = salesColIndex(header, 'sync_status');
  if (iStatus < 0) return [];
  var iCullId = salesColIndex(header, 'cull_id');
  var iAcc = salesColIndex(header, 'accession');
  var iQty = salesColIndex(header, 'qty');
  var iUnit = salesColIndex(header, 'unit');
  var iNotes = salesColIndex(header, 'notes');
  var iName = salesColIndex(header, 'name');

  var out = [];
  for (var r = 1; r < values.length; r++) {
    var row = values[r];
    if (String(row[iStatus]).trim().toLowerCase() !== 'pending') continue;
    out.push({
      cull_id: rowStr(row, iCullId),
      accession: rowStr(row, iAcc),
      qty: rowNum(row, iQty),
      unit: rowStr(row, iUnit),
      notes: rowStr(row, iNotes),
      name: rowStr(row, iName),
    });
  }
  return out;
}

/**
 * Backing logic for the `markCullsSynced` action: resolve a batch of mark requests against the raw
 * "Culls" sheet values (row 0 = header). Each request is `{cull_id, status}`; this finds the data row
 * whose `cull_id` primary key matches (see cullRowKey) and pairs it with the requested status.
 *
 * Returns `[{ rowIndex, status }]` where `rowIndex` is the 0-based index into `values` (so the header is
 * row 0 and the caller writes sheet row `rowIndex + 1`). A key with no matching row is silently ignored.
 */
function resolveCullMarks(values, keys) {
  if (!values || values.length < 2 || !keys || keys.length === 0) return [];
  var header = values[0];
  var iCullId = salesColIndex(header, 'cull_id');
  if (iCullId < 0) return [];

  var byKey = Object.create(null);
  for (var r = 1; r < values.length; r++) {
    var k = cullRowKey(values[r][iCullId]);
    if (byKey[k] === undefined) byKey[k] = r;
  }

  var out = [];
  (keys || []).forEach(function (req) {
    var rowIndex = byKey[cullRowKey(req.cull_id)];
    if (rowIndex !== undefined) {
      var mark = { rowIndex: rowIndex, status: req.status };
      var enrichment = pickPlantEnrichment(req);
      if (enrichment) mark.enrichment = enrichment;
      out.push(mark);
    }
  });
  return out;
}

/**
 * Normalised queue_id primary key for a PrintQueue row — trimmed string so " PP-1 " and "PP-1" collide.
 */
function printLabelRowKey(queueId) {
  return String(queueId === undefined || queueId === null ? '' : queueId).trim();
}

/**
 * Backing logic for the `pendingPrintLabels` action: select the reverse-sync work set from the raw
 * "PrintQueue" sheet values (row 0 = header). Returns every row whose `sync_status` is exactly
 * "Pending" (trimmed, case-insensitive), shaped as:
 * `{queue_id, date, accession, name, copies}` for Access PrintQueue insert + Batches print tracking.
 *
 * Columns are resolved by header name. A header-only or empty sheet, or one with no `sync_status`
 * column, yields [].
 */
function selectPendingPrintLabels(values) {
  if (!values || values.length < 2) return [];
  var header = values[0];
  var iStatus = salesColIndex(header, 'sync_status');
  if (iStatus < 0) return [];
  var iQueueId = salesColIndex(header, 'queue_id');
  var iDate = salesColIndex(header, 'date');
  var iAcc = salesColIndex(header, 'accession');
  var iName = salesColIndex(header, 'name');
  var iCopies = salesColIndex(header, 'copies');

  var out = [];
  for (var r = 1; r < values.length; r++) {
    var row = values[r];
    if (String(row[iStatus]).trim().toLowerCase() !== 'pending') continue;
    out.push({
      queue_id: rowStr(row, iQueueId),
      date: rowStr(row, iDate),
      accession: rowStr(row, iAcc),
      name: rowStr(row, iName),
      copies: rowNum(row, iCopies),
    });
  }
  return out;
}

/**
 * Backing logic for the `markPrintLabelsSynced` action: resolve mark requests against the raw
 * "PrintQueue" sheet values (row 0 = header). Each request is `{queue_id, status}`; this finds the
 * data row whose `queue_id` primary key matches and pairs it with the requested status.
 *
 * Returns `[{ rowIndex, status }]` (0-based into `values`). Unknown keys are silently ignored.
 */
function resolvePrintLabelMarks(values, keys) {
  if (!values || values.length < 2 || !keys || keys.length === 0) return [];
  var header = values[0];
  var iQueueId = salesColIndex(header, 'queue_id');
  if (iQueueId < 0) return [];

  var byKey = Object.create(null);
  for (var r = 1; r < values.length; r++) {
    var k = printLabelRowKey(values[r][iQueueId]);
    if (byKey[k] === undefined) byKey[k] = r;
  }

  var out = [];
  (keys || []).forEach(function (req) {
    var rowIndex = byKey[printLabelRowKey(req.queue_id)];
    if (rowIndex !== undefined) {
      out.push({ rowIndex: rowIndex, status: req.status });
    }
  });
  return out;
}

/**
 * Pure helper mirroring Code.gs mark application: write sync_status and, when the row's current name
 * is unknown, optional plant enrichment columns. Returns a deep copy of values with updates applied.
 */
function applyMarksToValues(values, marks) {
  if (!values || values.length < 2 || !marks || marks.length === 0) {
    return values ? values.map(function (row) { return row.slice(); }) : values;
  }
  var out = values.map(function (row) { return row.slice(); });
  var header = out[0];
  var iStatus = headerColIndex(header, 'sync_status');
  var iName = headerColIndex(header, 'name');
  var colByField = {};
  PLANT_ENRICHMENT_FIELDS.forEach(function (f) {
    colByField[f] = headerColIndex(header, f);
  });

  marks.forEach(function (mark) {
    var row = out[mark.rowIndex];
    if (!row) return;
    if (iStatus >= 0) row[iStatus] = mark.status;
    if (!mark.enrichment || iName < 0 || !isUnknownPlantName(row[iName])) return;
    PLANT_ENRICHMENT_FIELDS.forEach(function (f) {
      var col = colByField[f];
      if (col >= 0 && mark.enrichment[f] !== undefined) row[col] = mark.enrichment[f];
    });
  });
  return out;
}

/**
 * Access-parity helpers (not used by Code.gs): mirrored in backend/access/modPlantSync.bas.
 * Kept here for Node tests that lock the spec shared with Access reverse-sync.
 */

/** True when a cull's notes and unit identify a stock-plant cull per #28 UX. */
function isStockPlantCull(notes, unit) {
  if (String(notes === undefined || notes === null ? '' : notes).trim().toLowerCase() !== 'stock plant') {
    return false;
  }
  var u = String(unit === undefined || unit === null ? '' : unit).trim().toLowerCase();
  return u === 'pots' || u === 'pot';
}

/**
 * Pure cull deduction arithmetic (#28): subtract qty ONLY from the named container type, clamped at
 * zero. Unlike sales, misc oversell does NOT draw from pots. StockInNursery is never touched.
 * Unit strings must match SaleUnit in core/.
 */
function computeCullDeduction(unit, qty, pots, tubes, misc) {
  var u = String(unit === undefined || unit === null ? '' : unit).trim().toLowerCase();
  if (qty < 0) qty = 0;
  var p = pots, t = tubes, m = misc;
  function sub(cur) { return cur - qty < 0 ? 0 : cur - qty; }
  switch (u) {
    case 'pots':
    case 'pot': p = sub(p); break;
    case 'tubes':
    case 'tube': t = sub(t); break;
    case 'misc': m = sub(m); break;
  }
  return { pots: p, tubes: t, misc: m };
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = {
    isAuthorized, emptyToNull, rowStr, rowNum, composePlantName, isUnknownPlantName,
    pickPlantEnrichment, PLANT_ENRICHMENT_FIELDS, parsePlants, filterNewRows, planPlantReplace,
    findRowByKey, accessionColIndex, headerColIndex, salesColIndex, salesRowKey,
    selectPendingSales, resolveSalesMarks,
    ensureSyncStatusColumn, validateAppendCullsNotes, cullRowKey, selectPendingCulls, resolveCullMarks,
    printLabelRowKey, selectPendingPrintLabels, resolvePrintLabelMarks,
    isStockPlantCull, computeCullDeduction, applyMarksToValues,
  };
}
