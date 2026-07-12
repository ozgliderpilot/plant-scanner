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
 * Opaque plant-list fingerprint from the parsed `getPlants` plant objects (not raw sheet cells).
 * Deterministic across Node and Apps Script — no GAS-only APIs.
 */
function computePlantListFingerprint(plants) {
  var lines = (plants || []).map(function (p) {
    return [
      String(p.accession || ''),
      String(p.name || ''),
      String(p.genus || ''),
      String(p.species || ''),
      String(p.cultivar || ''),
      String(p.commonName || ''),
      p.group == null ? '' : String(p.group),
      p.light == null ? '' : String(p.light),
      String(Number(p.potsInNursery) || 0),
      String(Number(p.tubesInNursery) || 0),
      String(Number(p.miscInNursery) || 0),
      String(Number(p.stockInNursery) || 0),
    ].join('\t');
  });
  return hashPlantListCanonical_(lines.join('\n'));
}

/** True when the device-sent fingerprint matches the server cache (both non-blank). */
function plantListFingerprintMatches(requestFingerprint, cachedFingerprint) {
  var req = String(requestFingerprint == null ? '' : requestFingerprint).trim();
  var cached = String(cachedFingerprint == null ? '' : cachedFingerprint).trim();
  return req !== '' && cached !== '' && req === cached;
}

/** FNV-1a 32-bit × 2 → 16 hex chars. Pure JS so Node tests and GAS share one implementation. */
function hashPlantListCanonical_(s) {
  var h1 = 2166136261;
  var h2 = 2166136261 ^ 0x9e3779b9;
  for (var i = 0; i < s.length; i++) {
    var c = s.charCodeAt(i);
    h1 ^= c;
    h1 = Math.imul(h1, 16777619) >>> 0;
    h2 ^= c;
    h2 = Math.imul(h2, 16777619) >>> 0;
  }
  return toHex8_(h1) + toHex8_(h2);
}

function toHex8_(n) {
  var h = n.toString(16);
  return ('00000000' + h).slice(-8);
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

/** Max data rows kept in the SyncStatus rolling log (header excluded). */
var SYNC_STATUS_MAX_ROWS = 100;

/**
 * Prepend `newRow` to SyncStatus data rows (newest first) and trim to SYNC_STATUS_MAX_ROWS.
 * Does not mutate `existingRows`.
 */
function planSyncStatusLog(existingRows, newRow) {
  var rows = [newRow].concat(existingRows || []);
  if (rows.length > SYNC_STATUS_MAX_ROWS) rows = rows.slice(0, SYNC_STATUS_MAX_ROWS);
  return rows;
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
 * Reject appendPrintLabels payloads whose copies column is not a positive integer within a safe
 * bound. Prevents negative / extreme values from corrupting Access NoPrinted tracking if a client
 * with the shared secret sends a crafted payload. Returns an error message when invalid, null when OK.
 */
var PRINT_LABEL_COPIES_MAX = 10000;

function validateAppendPrintLabelCopies(header, rows) {
  var iCopies = salesColIndex(header, 'copies');
  if (iCopies < 0) return 'Missing copies column';
  for (var r = 0; r < (rows || []).length; r++) {
    var row = rows[r];
    var raw = row && iCopies < row.length ? row[iCopies] : '';
    var n = Number(raw);
    // Integer 1..MAX (Apps Script–safe; avoid Number.isInteger).
    if (!(isFinite(n) && Math.floor(n) === n && n >= 1 && n <= PRINT_LABEL_COPIES_MAX)) {
      return 'Print label copies must be an integer from 1 to ' + PRINT_LABEL_COPIES_MAX;
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
 * data row whose `queue_id` primary key matches (see cullRowKey) and pairs it with the requested status.
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
    var k = cullRowKey(values[r][iQueueId]);
    if (byKey[k] === undefined) byKey[k] = r;
  }

  var out = [];
  (keys || []).forEach(function (req) {
    var rowIndex = byKey[cullRowKey(req.queue_id)];
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

/**
 * Pure sales deduction arithmetic (#80): mirrors Access ComputeDeduction_ / DeductSelfTest.
 * Subtract qty from the named container, clamped at zero. Misc oversell overflows into pots.
 * Blank/unrecognized unit moves nothing. StockInNursery is never touched.
 * Unit strings must match SaleUnit in core/ (pots/tubes/misc).
 */
function computeSalesDeduction(unit, qty, pots, tubes, misc) {
  var u = String(unit === undefined || unit === null ? '' : unit).trim().toLowerCase();
  if (qty < 0) qty = 0;
  var p = pots, t = tubes, m = misc;
  switch (u) {
    case 'pots':
      p = Math.max(0, p - qty);
      break;
    case 'tubes':
      t = Math.max(0, t - qty);
      break;
    case 'misc': {
      // Access ClampSub_(qty, m): shortfall is misc sold beyond MiscInNursery, then drawn from pots.
      var shortfall = Math.max(0, qty - m);
      m = Math.max(0, m - qty);
      p = Math.max(0, p - shortfall);
      break;
    }
  }
  return { pots: p, tubes: t, misc: m };
}

/**
 * Predict Plants-tab stock cell updates for newly appended sales or cull rows (#80).
 * Only accessions present on the Plants tab are updated; unknown scans are skipped.
 * Stock-plant culls are skipped. StockInNursery is never included in the result.
 * Returns one entry per touched plant row: { rowIndex, pots, tubes, misc } (0-based in values).
 */
function predictStockUpdates(plantsValues, exportHeader, appendedRows, kind) {
  var deduct = kind === 'sales' ? computeSalesDeduction
    : kind === 'culls' ? computeCullDeduction
    : null;
  if (!deduct || !plantsValues || plantsValues.length < 2 || !appendedRows || !appendedRows.length) {
    return [];
  }
  var pHeader = plantsValues[0];
  var iAcc = accessionColIndex(pHeader);
  var iPots = headerColIndex(pHeader, 'potsinnursery');
  var iTubes = headerColIndex(pHeader, 'tubesinnursery');
  var iMisc = headerColIndex(pHeader, 'miscinnursery');
  if (iAcc < 0 || iPots < 0 || iTubes < 0 || iMisc < 0) return [];

  var iRowAcc = headerColIndex(exportHeader, 'accession');
  var iQty = headerColIndex(exportHeader, 'qty');
  var iUnit = headerColIndex(exportHeader, 'unit');
  var iNotes = headerColIndex(exportHeader, 'notes');
  if (iRowAcc < 0 || iQty < 0 || iUnit < 0) return [];

  var byAcc = Object.create(null);
  for (var r = 1; r < plantsValues.length; r++) {
    var acc = String(plantsValues[r][iAcc]).trim();
    if (acc && byAcc[acc] === undefined) byAcc[acc] = r;
  }

  var touched = Object.create(null); // accession -> { rowIndex, pots, tubes, misc }
  for (var i = 0; i < appendedRows.length; i++) {
    var row = appendedRows[i];
    var accession = rowStr(row, iRowAcc);
    var plantRow = byAcc[accession];
    if (plantRow === undefined) continue;

    var unit = rowStr(row, iUnit);
    if (kind === 'culls' && isStockPlantCull(rowStr(row, iNotes), unit)) continue;

    var state = touched[accession];
    if (!state) {
      state = {
        rowIndex: plantRow,
        pots: rowNum(plantsValues[plantRow], iPots),
        tubes: rowNum(plantsValues[plantRow], iTubes),
        misc: rowNum(plantsValues[plantRow], iMisc),
      };
      touched[accession] = state;
    }

    var next = deduct(unit, rowNum(row, iQty), state.pots, state.tubes, state.misc);
    state.pots = next.pots;
    state.tubes = next.tubes;
    state.misc = next.misc;
  }

  var out = [];
  Object.keys(touched).forEach(function (k) {
    var s = touched[k];
    out.push({ rowIndex: s.rowIndex, pots: s.pots, tubes: s.tubes, misc: s.misc });
  });
  out.sort(function (a, b) { return a.rowIndex - b.rowIndex; });
  return out;
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = {
    isAuthorized, emptyToNull, rowStr, rowNum, composePlantName, isUnknownPlantName,
    pickPlantEnrichment, PLANT_ENRICHMENT_FIELDS, parsePlants, filterNewRows, planPlantReplace,
    planSyncStatusLog, SYNC_STATUS_MAX_ROWS,
    accessionColIndex, headerColIndex, salesColIndex, salesRowKey,
    selectPendingSales, resolveSalesMarks,
    ensureSyncStatusColumn, validateAppendCullsNotes, cullRowKey, selectPendingCulls, resolveCullMarks,
    selectPendingPrintLabels, resolvePrintLabelMarks, validateAppendPrintLabelCopies,
    PRINT_LABEL_COPIES_MAX,
    isStockPlantCull, computeCullDeduction, computeSalesDeduction, predictStockUpdates,
    applyMarksToValues,
    computePlantListFingerprint, plantListFingerprintMatches,
  };
}
