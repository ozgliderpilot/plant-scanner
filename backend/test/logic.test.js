const test = require('node:test');
const assert = require('node:assert');
const {
  isAuthorized, emptyToNull, parsePlants, composePlantName, filterNewRows, planPlantReplace,
  planSyncStatusLog, SYNC_STATUS_MAX_ROWS,
  accessionColIndex, selectPendingSales, resolveSalesMarks, ensureSyncStatusColumn,
  selectPendingCulls, resolveCullMarks, isStockPlantCull, computeCullDeduction, computeSalesDeduction,
  predictStockUpdates,
  validateAppendCullsNotes, applyMarksToValues,
  selectPendingPrintLabels, resolvePrintLabelMarks, validateAppendPrintLabelCopies,
  PRINT_LABEL_COPIES_MAX,
  computePlantListFingerprint, plantListFingerprintMatches,
} = require('../shared.js');

test('isAuthorized accepts the right secret only', () => {
  assert.strictEqual(isAuthorized({ secret: 's3cr3t' }, 's3cr3t'), true);
  assert.strictEqual(isAuthorized({ secret: 'wrong' }, 's3cr3t'), false);
  assert.strictEqual(isAuthorized({}, 's3cr3t'), false);
  assert.strictEqual(isAuthorized({ secret: 's3cr3t' }, ''), false); // no secret configured
  assert.strictEqual(isAuthorized(null, 's3cr3t'), false);
});

test('emptyToNull normalises blanks', () => {
  assert.strictEqual(emptyToNull(''), null);
  assert.strictEqual(emptyToNull('  '), null);
  assert.strictEqual(emptyToNull(null), null);
  assert.strictEqual(emptyToNull('x'), 'x');
});

test('parsePlants maps by header name and skips blank accessions', () => {
  const values = [
    ['accession', 'name', 'group', 'light'],
    ['2021-0345', 'Banksia', 'Proteaceae', 'Full sun'],
    ['', 'ignored', '', ''], // blank accession -> skipped
    ['2022-0100', 'Wattle', 'Fabaceae', ''],
  ];
  const plants = parsePlants(values);
  assert.strictEqual(plants.length, 2);
  assert.deepStrictEqual(plants[0], {
    accession: '2021-0345', name: 'Banksia', genus: '', species: '', cultivar: '', commonName: '',
    group: 'Proteaceae', light: 'Full sun',
    potsInNursery: 0, tubesInNursery: 0, miscInNursery: 0, stockInNursery: 0,
  });
  // blank optional cells become null
  assert.strictEqual(plants[1].group, 'Fabaceae');
  assert.strictEqual(plants[1].light, null);
});

test('parsePlants tolerates reordered / missing optional columns', () => {
  const values = [
    ['name', 'accession'],
    ['Wattle', '2022-0100'],
  ];
  const plants = parsePlants(values);
  assert.strictEqual(plants.length, 1);
  assert.strictEqual(plants[0].accession, '2022-0100');
  assert.strictEqual(plants[0].name, 'Wattle');
  assert.strictEqual(plants[0].group, null);
});

test('computePlantListFingerprint is stable for the same parsed plants', () => {
  const plants = parsePlants([
    ['accession', 'name', 'potsInNursery', 'tubesInNursery', 'miscInNursery', 'stockInNursery'],
    ['2021-0345', 'Banksia', 2, 0, 0, 1],
    ['2022-0100', 'Wattle', 0, 3, 0, 0],
  ]);
  const a = computePlantListFingerprint(plants);
  const b = computePlantListFingerprint(plants);
  assert.strictEqual(typeof a, 'string');
  assert.ok(a.length > 0);
  assert.strictEqual(a, b);
});

test('computePlantListFingerprint changes when stock counts change', () => {
  const header = ['accession', 'name', 'potsInNursery', 'tubesInNursery', 'miscInNursery', 'stockInNursery'];
  const before = computePlantListFingerprint(parsePlants([
    header, ['2021-0345', 'Banksia', 2, 0, 0, 1],
  ]));
  const after = computePlantListFingerprint(parsePlants([
    header, ['2021-0345', 'Banksia', 1, 0, 0, 1],
  ]));
  assert.notStrictEqual(before, after);
});

test('computePlantListFingerprint changes when accession order changes', () => {
  const header = ['accession', 'name', 'potsInNursery', 'tubesInNursery', 'miscInNursery', 'stockInNursery'];
  const a = computePlantListFingerprint(parsePlants([
    header, ['2021-0345', 'Banksia', 1, 0, 0, 0], ['2022-0100', 'Wattle', 1, 0, 0, 0],
  ]));
  const b = computePlantListFingerprint(parsePlants([
    header, ['2022-0100', 'Wattle', 1, 0, 0, 0], ['2021-0345', 'Banksia', 1, 0, 0, 0],
  ]));
  assert.notStrictEqual(a, b);
});

test('computePlantListFingerprint for empty list is stable and non-empty', () => {
  const fp = computePlantListFingerprint([]);
  assert.strictEqual(fp, computePlantListFingerprint([]));
  assert.ok(fp.length > 0);
});

test('plantListFingerprintMatches requires both sides non-blank and equal', () => {
  assert.strictEqual(plantListFingerprintMatches('abc', 'abc'), true);
  assert.strictEqual(plantListFingerprintMatches(' abc ', 'abc'), true);
  assert.strictEqual(plantListFingerprintMatches('abc', 'xyz'), false);
  assert.strictEqual(plantListFingerprintMatches('', 'abc'), false);
  assert.strictEqual(plantListFingerprintMatches('abc', ''), false);
  assert.strictEqual(plantListFingerprintMatches(null, 'abc'), false);
  assert.strictEqual(plantListFingerprintMatches('abc', null), false);
});

test('filterNewRows skips already-exported receipts, keeps all lines of new ones', () => {
  const incoming = [
    ['07-1', 'banksia-line-a'],
    ['07-1', 'banksia-line-b'], // same NEW receipt, two lines -> both kept
    ['07-2', 'already-there'],  // already in the sheet -> skipped
  ];
  const existing = ['07-2'];
  const result = filterNewRows(incoming, existing, 0);
  assert.strictEqual(result.skipped, 1);
  assert.strictEqual(result.rows.length, 2);
  assert.deepStrictEqual(result.rows.map((r) => r[0]), ['07-1', '07-1']);
});

test('filterNewRows on empty inputs', () => {
  assert.deepStrictEqual(filterNewRows([], [], 0), { rows: [], skipped: 0 });
  assert.deepStrictEqual(filterNewRows(null, null, 0), { rows: [], skipped: 0 });
});

// Header now carries the stock columns appended after the app-compatible accession/name/group/light.
const PLANTS_HEADER = [
  'accession', 'name', 'group', 'light',
  'potsInNursery', 'tubesInNursery', 'miscInNursery', 'stockInNursery',
  'potsForSale', 'tubesForSale', 'miscForSale',
];

test('planPlantReplace returns rows in order, passing the header through unchanged', () => {
  const rows = [
    ['31011', 'Acacia pycnantha', 'Tree', 'Full sun', 2, 0, 0, 0, true, false, false],
    ['8250', 'Banksia integrifolia', 'Shrub', 'Full sun', 0, 0, 0, 1, false, false, false],
  ];
  const plan = planPlantReplace(PLANTS_HEADER, rows);
  assert.deepStrictEqual(plan.header, PLANTS_HEADER);
  assert.strictEqual(plan.rows.length, 2);
  assert.deepStrictEqual(plan.rows.map((r) => r[0]), ['31011', '8250']);
});

test('planPlantReplace skips rows with a blank accession', () => {
  const plan = planPlantReplace(PLANTS_HEADER, [
    ['', 'no key', '', '', 0, 0, 0, 0, false, false, false],
    ['9415', 'Keeper', '', '', 0, 0, 0, 1, false, false, false],
  ]);
  assert.strictEqual(plan.rows.length, 1);
  assert.strictEqual(plan.rows[0][0], '9415');
});

test('planPlantReplace dedupes a repeated accession, last wins', () => {
  const plan = planPlantReplace(PLANTS_HEADER, [
    ['10085', 'First', '', '', 1, 0, 0, 0, true, false, false],
    ['10085', 'Second', '', '', 5, 0, 0, 0, true, false, false],
  ]);
  assert.strictEqual(plan.rows.length, 1);
  assert.strictEqual(plan.rows[0][1], 'Second');
  assert.strictEqual(plan.rows[0][4], 5);
});

test('planPlantReplace finds accession by header name even if not first column', () => {
  const header = ['name', 'accession', 'potsInNursery'];
  const plan = planPlantReplace(header, [
    ['Grevillea', '16726', 9],
    ['', '', 0],          // blank accession -> skipped
    ['Hardenbergia', '17712', 7],
  ]);
  assert.strictEqual(plan.rows.length, 2);
  assert.deepStrictEqual(plan.rows.map((r) => r[1]), ['16726', '17712']);
});

test('planPlantReplace on empty inputs', () => {
  assert.deepStrictEqual(planPlantReplace([], []), { header: [], rows: [] });
  assert.deepStrictEqual(planPlantReplace(PLANTS_HEADER, []).rows, []);
});

test('planSyncStatusLog prepends the new event as the first data row', () => {
  const existing = [
    ['Sales from device', 'device → Sheet', 't1', '2 receipts'],
    ['Plants from Access', 'Access → Sheet', 't0', '10 plants'],
  ];
  const newer = ['Plant list to device', 'Sheet → device', 't2', '10 plants'];
  assert.deepStrictEqual(planSyncStatusLog(existing, newer), [
    newer,
    existing[0],
    existing[1],
  ]);
});

test('planSyncStatusLog keeps duplicate event labels as separate rows', () => {
  const existing = [
    ['Sales from device', 'device → Sheet', 't1', '1 receipt'],
  ];
  const newer = ['Sales from device', 'device → Sheet', 't2', '3 receipts'];
  const planned = planSyncStatusLog(existing, newer);
  assert.strictEqual(planned.length, 2);
  assert.deepStrictEqual(planned[0], newer);
  assert.deepStrictEqual(planned[1], existing[0]);
});

test('planSyncStatusLog trims to 100 data rows, dropping the oldest', () => {
  assert.strictEqual(SYNC_STATUS_MAX_ROWS, 100);
  const existing = [];
  for (let i = 0; i < 100; i++) {
    existing.push(['Event ' + i, 'dir', 't' + i, 'detail ' + i]);
  }
  const newer = ['Newest', 'dir', 't-new', 'fresh'];
  const planned = planSyncStatusLog(existing, newer);
  assert.strictEqual(planned.length, 100);
  assert.deepStrictEqual(planned[0], newer);
  assert.deepStrictEqual(planned[99], existing[98]); // oldest (existing[99]) dropped
  assert.strictEqual(planned.some((r) => r[0] === 'Event 99'), false);
});

test('planSyncStatusLog treats null/empty existing rows as an empty log', () => {
  const newer = ['Sales from device', 'device → Sheet', 't0', '1 receipt'];
  assert.deepStrictEqual(planSyncStatusLog(null, newer), [newer]);
  assert.deepStrictEqual(planSyncStatusLog([], newer), [newer]);
});

test('planPlantReplace keys on raw "Ac Number" header when not column 0', () => {
  const header = ['Genus', 'Ac Number', 'PotsInNursery'];
  const plan = planPlantReplace(header, [
    ['Acacia', '31011', 2],
    ['Banksia', '', 0],     // blank accession -> skipped
    ['Acacia', '31011', 9], // dup -> last wins
  ]);
  assert.strictEqual(plan.rows.length, 1);
  assert.strictEqual(plan.rows[0][2], 9);
});

test('parsePlants reads the raw Batches+Species view and composes name', () => {
  const values = [
    ['Ac Number', 'Id No', 'Genus', 'Species', 'Cultivar', 'Common Name', 'Plant Type', 'Sun/Shade', 'StockInNursery'],
    ['31011', '7837', 'Acacia', 'pycnantha', '', 'Golden Wattle', 'Tree', 'Full sun', 2],
    ['8250', '4411', 'Banksia', 'integrifolia', 'Roller Coaster', '', 'Shrub', 'Part shade', 1],
    ['', '0', 'Ignored', 'ignored', '', '', '', '', 0], // blank accession -> skipped
  ];
  const plants = parsePlants(values);
  assert.strictEqual(plants.length, 2);
  assert.deepStrictEqual(plants[0], {
    accession: '31011', name: 'Acacia pycnantha', genus: 'Acacia', species: 'pycnantha',
    cultivar: '', commonName: 'Golden Wattle', group: 'Tree', light: 'Full sun',
    potsInNursery: 0, tubesInNursery: 0, miscInNursery: 0, stockInNursery: 2,
  });
  // cultivar folded into the name
  assert.strictEqual(plants[1].name, "Banksia integrifolia 'Roller Coaster'");
  assert.strictEqual(plants[1].genus, 'Banksia');
  assert.strictEqual(plants[1].species, 'integrifolia');
  assert.strictEqual(plants[1].cultivar, 'Roller Coaster');
  assert.strictEqual(plants[1].group, 'Shrub');
});

test('parsePlants emits taxonomic parts separately alongside composed name', () => {
  const values = [
    ['accession', 'genus', 'species', 'cultivar', 'common name'],
    ['100', 'Grevillea', 'rosmarinifolia', 'Pink', 'Rose Grevillea'],
  ];
  const p = parsePlants(values)[0];
  assert.strictEqual(p.name, "Grevillea rosmarinifolia 'Pink'");
  assert.strictEqual(p.genus, 'Grevillea');
  assert.strictEqual(p.species, 'rosmarinifolia');
  assert.strictEqual(p.cultivar, 'Pink');
  assert.strictEqual(p.commonName, 'Rose Grevillea');
});

test('parsePlants name falls back to Common Name, and legacy headers still work', () => {
  assert.strictEqual(
    parsePlants([['Ac Number', 'Genus', 'Common Name'], ['9', '', 'Mystery Plant']])[0].name,
    'Mystery Plant',
  );
  // old-style sheet (accession/name/group/light) keeps parsing
  const legacy = parsePlants([['accession', 'name', 'group', 'light'], ['2021-1', 'Wattle', 'Tree', 'Sun']]);
  assert.deepStrictEqual(legacy[0], {
    accession: '2021-1', name: 'Wattle', genus: '', species: '', cultivar: '', commonName: '',
    group: 'Tree', light: 'Sun',
    potsInNursery: 0, tubesInNursery: 0, miscInNursery: 0, stockInNursery: 0,
  });
});

test('planPlantReplace keeps accessions that collide with Object prototype keys', () => {
  // Accession values are free text; with a plain {} dedupe map these would look "already present"
  // (byKey['__proto__'] etc. are inherited) and the rows would vanish silently. The map must be
  // prototype-less (Object.create(null)).
  const plan = planPlantReplace(PLANTS_HEADER, [
    ['__proto__', 'Proto Plant', '', ''],
    ['constructor', 'Ctor Plant', '', ''],
    ['toString', 'Str Plant', '', ''],
  ]);
  assert.strictEqual(plan.rows.length, 3);
  assert.deepStrictEqual(plan.rows.map((r) => r[0]), ['__proto__', 'constructor', 'toString']);
});

test('accessionColIndex matches the app and raw-Access header names, else -1', () => {
  assert.strictEqual(accessionColIndex(['accession', 'name']), 0);
  assert.strictEqual(accessionColIndex(['Genus', 'Ac Number', 'Pots']), 1); // raw Access, case-insensitive
  assert.strictEqual(accessionColIndex(['name', 'group', 'light']), -1);
  assert.strictEqual(accessionColIndex([]), -1);
});

test('parsePlants reads the per-accession stock counts (Nz -> 0)', () => {
  const values = [
    ['Ac Number', 'Genus', 'Species', 'PotsInNursery', 'TubesInNursery', 'MiscInNursery'],
    ['31011', 'Acacia', 'pycnantha', 5, 2, 0],
    ['8250', 'Banksia', 'integrifolia', '', '', ''], // blanks -> 0
    ['9000', 'Grevillea', 'robusta', '3', '0', '1'], // string numerics (GAS getValues) -> numbers
  ];
  const plants = parsePlants(values);
  assert.strictEqual(plants[0].potsInNursery, 5);
  assert.strictEqual(plants[0].tubesInNursery, 2);
  assert.strictEqual(plants[0].miscInNursery, 0);
  assert.strictEqual(plants[1].potsInNursery, 0);
  assert.strictEqual(plants[1].tubesInNursery, 0);
  assert.strictEqual(plants[1].miscInNursery, 0);
  assert.strictEqual(plants[2].potsInNursery, 3);
  assert.strictEqual(plants[2].tubesInNursery, 0);
  assert.strictEqual(plants[2].miscInNursery, 1);
});

test('parsePlants defaults stock counts to 0 when the columns are absent', () => {
  const plants = parsePlants([['Ac Number', 'Genus'], ['9', 'Grevillea']]);
  assert.strictEqual(plants[0].potsInNursery, 0);
  assert.strictEqual(plants[0].tubesInNursery, 0);
  assert.strictEqual(plants[0].miscInNursery, 0);
  assert.strictEqual(plants[0].stockInNursery, 0);
});

test('parsePlants reads StockInNursery (Nz -> 0)', () => {
  const values = [
    ['Ac Number', 'Genus', 'StockInNursery'],
    ['31011', 'Acacia', 3],      // numeric
    ['8250', 'Banksia', '4'],    // string numeric (GAS getValues)
    ['9000', 'Grevillea', ''],   // blank -> 0
    ['9001', 'Hakea', 'nope'],   // non-numeric -> 0
  ];
  const plants = parsePlants(values);
  assert.strictEqual(plants[0].stockInNursery, 3);
  assert.strictEqual(plants[1].stockInNursery, 4);
  assert.strictEqual(plants[2].stockInNursery, 0);
  assert.strictEqual(plants[3].stockInNursery, 0);
});

// ---- Reverse sync (Sales -> Access) selection & marking -------------------------------------------
// Keep in sync with Export.HEADER in core/Export.kt, plus sheet-only sync_status.
const SALES_HEADER = [
  'receipt', 'date', 'item_seq', 'accession', 'name',
  'genus', 'species', 'cultivar', 'common_name', 'group',
  'qty', 'unit', 'unit_price', 'discount_pct', 'line_total', 'payment_method', 'sync_status',
];

/** Build a full-width Sales sheet row; taxonomic/group fields default to empty. */
function salesRow(receipt, date, itemSeq, accession, name, qty, unit, unitPrice, discount, lineTotal, syncStatus, paymentMethod = 'card') {
  return [receipt, date, itemSeq, accession, name, '', '', '', '', '', qty, unit, unitPrice, discount, lineTotal, paymentMethod, syncStatus];
}

test('selectPendingSales returns [] for an empty or header-only sheet', () => {
  assert.deepStrictEqual(selectPendingSales(null), []);
  assert.deepStrictEqual(selectPendingSales([]), []);
  assert.deepStrictEqual(selectPendingSales([SALES_HEADER]), []);
});

test('selectPendingSales selects only Pending rows, shaped {receipt,item_seq,accession,qty,unit,name}', () => {
  const values = [
    SALES_HEADER,
    salesRow('PP-1700000000-1', '2026-06-23', 1, '31011', 'Acacia', 2, 'pots', 500, 0, 1000, 'Pending'),
    salesRow('PP-1700000000-2', '2026-06-23', 2, '8250', 'Banksia', 1, 'tubes', 300, 0, 300, 'Synced'),
    salesRow('PP-1700000000-3', '2026-06-23', 3, '9000', 'Grevillea', 5, 'misc', 100, 0, 500, 'NoMatch'),
    salesRow('PP-1700000000-4', '2026-06-23', 4, '16726', 'Hardenbergia', 3, 'pots', 200, 0, 600, 'Pending'),
  ];
  assert.deepStrictEqual(selectPendingSales(values), [
    { receipt: 'PP-1700000000-1', item_seq: 1, accession: '31011', qty: 2, unit: 'pots', name: 'Acacia' },
    { receipt: 'PP-1700000000-4', item_seq: 4, accession: '16726', qty: 3, unit: 'pots', name: 'Hardenbergia' },
  ]);
});

test('selectPendingSales matches sync_status case-insensitively after trimming', () => {
  const values = [SALES_HEADER, salesRow('PP-9-1', '2026-06-23', 1, '31011', 'A', 2, 'pots', 500, 0, 1000, '  pending ')];
  assert.strictEqual(selectPendingSales(values).length, 1);
});

test('selectPendingSales parses item_seq/qty as numbers and keeps receipt/accession as strings', () => {
  // GAS getValues can hand back the numeric cells as strings; identifier cells are stored as text.
  const values = [SALES_HEADER, salesRow('07-1', '2026-06-23', '3', '31011', 'Acacia', '2', 'pots', 500, 0, 1000, 'Pending')];
  const s = selectPendingSales(values)[0];
  assert.strictEqual(s.item_seq, 3);
  assert.strictEqual(typeof s.item_seq, 'number');
  assert.strictEqual(s.qty, 2);
  assert.strictEqual(typeof s.qty, 'number');
  assert.strictEqual(s.receipt, '07-1');
  assert.strictEqual(typeof s.receipt, 'string');
  assert.strictEqual(s.accession, '31011');
  assert.strictEqual(typeof s.accession, 'string');
});

test('selectPendingSales tolerates reordered columns and a missing data column', () => {
  const header = ['sync_status', 'item_seq', 'receipt', 'accession', 'qty']; // no unit/name columns
  const values = [header, ['Pending', 2, 'PP-5-2', '8250', 4]];
  assert.deepStrictEqual(selectPendingSales(values), [
    { receipt: 'PP-5-2', item_seq: 2, accession: '8250', qty: 4, unit: '', name: '' },
  ]);
});

test('selectPendingSales carries unknown name for Access enrichment gating', () => {
  const values = [
    SALES_HEADER,
    salesRow('PP-9-1', '2026-06-23', 1, '31011', 'unknown', 1, 'pots', 500, 0, 500, 'Pending'),
  ];
  assert.deepStrictEqual(selectPendingSales(values), [
    { receipt: 'PP-9-1', item_seq: 1, accession: '31011', qty: 1, unit: 'pots', name: 'unknown' },
  ]);
});

test('selectPendingSales returns [] when there is no sync_status column', () => {
  const header = ['receipt', 'item_seq', 'accession', 'qty', 'unit'];
  assert.deepStrictEqual(selectPendingSales([header, ['PP-5-1', 1, '31011', 2, 'pots']]), []);
});

test('resolveSalesMarks maps each (receipt,item_seq) key to its values row index and status', () => {
  const values = [
    SALES_HEADER,
    salesRow('PP-1-1', '2026-06-23', 1, '31011', 'Acacia', 2, 'pots', 500, 0, 1000, 'Pending'),
    salesRow('PP-1-2', '2026-06-23', 2, '8250', 'Banksia', 1, 'pots', 300, 0, 300, 'Pending'),
  ];
  assert.deepStrictEqual(
    resolveSalesMarks(values, [{ receipt: 'PP-1-2', item_seq: 2, status: 'Synced' }]),
    [{ rowIndex: 2, status: 'Synced' }],
  );
});

test('resolveSalesMarks ignores keys with no matching row (not an error)', () => {
  const values = [SALES_HEADER, salesRow('PP-1-1', '2026-06-23', 1, '31011', 'A', 2, 'pots', 500, 0, 1000, 'Pending')];
  assert.deepStrictEqual(
    resolveSalesMarks(values, [
      { receipt: 'PP-1-1', item_seq: 1, status: 'Synced' },
      { receipt: 'PP-NOPE', item_seq: 9, status: 'Synced' }, // absent -> ignored
    ]),
    [{ rowIndex: 1, status: 'Synced' }],
  );
});

test('resolveSalesMarks handles multiple keys and mixed statuses in one batch', () => {
  const values = [
    SALES_HEADER,
    salesRow('PP-1-1', '2026-06-23', 1, '31011', 'A', 2, 'pots', 500, 0, 1000, 'Pending'),
    salesRow('PP-1-2', '2026-06-23', 2, '8250', 'B', 1, 'misc', 300, 0, 300, 'Pending'),
    salesRow('PP-1-3', '2026-06-23', 3, '9000', 'C', 1, 'pots', 100, 0, 100, 'Pending'),
  ];
  assert.deepStrictEqual(
    resolveSalesMarks(values, [
      { receipt: 'PP-1-1', item_seq: 1, status: 'Synced' },
      { receipt: 'PP-1-2', item_seq: 2, status: 'NoMatch' },
      { receipt: 'PP-1-3', item_seq: 3, status: 'Synced' },
    ]),
    [
      { rowIndex: 1, status: 'Synced' },
      { rowIndex: 2, status: 'NoMatch' },
      { rowIndex: 3, status: 'Synced' },
    ],
  );
});

test('resolveSalesMarks matches a key despite receipt whitespace and item_seq type drift', () => {
  const values = [SALES_HEADER, salesRow('PP-1-1', '2026-06-23', 7, '31011', 'A', 2, 'pots', 500, 0, 1000, 'Pending')];
  assert.deepStrictEqual(
    resolveSalesMarks(values, [{ receipt: '  PP-1-1 ', item_seq: '7', status: 'Synced' }]),
    [{ rowIndex: 1, status: 'Synced' }],
  );
});

test('resolveSalesMarks on empty keys or empty sheet returns []', () => {
  assert.deepStrictEqual(resolveSalesMarks([SALES_HEADER], [{ receipt: 'x', item_seq: 1, status: 'Synced' }]), []);
  assert.deepStrictEqual(resolveSalesMarks([SALES_HEADER, salesRow('PP-1-1', '', 1, '', '', 0, '', 0, 0, 0, 'Pending')], []), []);
  assert.deepStrictEqual(resolveSalesMarks(null, null), []);
});

test('composePlantName matches parsePlants name rules', () => {
  assert.strictEqual(composePlantName('Acacia', 'pycnantha', '', 'Golden Wattle'), 'Acacia pycnantha');
  assert.strictEqual(composePlantName('Banksia', 'integrifolia', 'Roller Coaster', ''), "Banksia integrifolia 'Roller Coaster'");
  assert.strictEqual(composePlantName('', '', '', 'Mystery Plant'), 'Mystery Plant');
});

test('resolveSalesMarks carries optional plant enrichment fields from keys', () => {
  const values = [SALES_HEADER, salesRow('PP-1-1', '2026-06-23', 1, '31011', 'unknown', 2, 'pots', 500, 0, 1000, 'Pending')];
  assert.deepStrictEqual(
    resolveSalesMarks(values, [{
      receipt: 'PP-1-1', item_seq: 1, status: 'Synced',
      name: 'Acacia pycnantha', genus: 'Acacia', species: 'pycnantha',
      cultivar: '', common_name: 'Golden Wattle', group: 'Tree',
    }]),
    [{
      rowIndex: 1, status: 'Synced',
      enrichment: {
        name: 'Acacia pycnantha', genus: 'Acacia', species: 'pycnantha',
        common_name: 'Golden Wattle', group: 'Tree',
      },
    }],
  );
});

test('applyMarksToValues enriches unknown sales rows when enrichment fields are present', () => {
  const values = [
    SALES_HEADER,
    salesRow('PP-1-1', '2026-06-23', 1, '31011', 'unknown', 2, 'pots', 500, 0, 1000, 'Pending'),
  ];
  const marks = resolveSalesMarks(values, [{
    receipt: 'PP-1-1', item_seq: 1, status: 'Synced',
    name: 'Acacia pycnantha', genus: 'Acacia', species: 'pycnantha',
    common_name: 'Golden Wattle', group: 'Tree',
  }]);
  const out = applyMarksToValues(values, marks);
  assert.strictEqual(out[1][SALES_HEADER.indexOf('sync_status')], 'Synced');
  assert.strictEqual(out[1][SALES_HEADER.indexOf('name')], 'Acacia pycnantha');
  assert.strictEqual(out[1][SALES_HEADER.indexOf('genus')], 'Acacia');
  assert.strictEqual(out[1][SALES_HEADER.indexOf('species')], 'pycnantha');
  assert.strictEqual(out[1][SALES_HEADER.indexOf('common_name')], 'Golden Wattle');
  assert.strictEqual(out[1][SALES_HEADER.indexOf('group')], 'Tree');
});

test('applyMarksToValues does not overwrite a non-unknown name', () => {
  const values = [
    SALES_HEADER,
    salesRow('PP-1-1', '2026-06-23', 1, '31011', 'Acacia', 2, 'pots', 500, 0, 1000, 'Pending'),
  ];
  const marks = resolveSalesMarks(values, [{
    receipt: 'PP-1-1', item_seq: 1, status: 'Synced',
    name: 'Should Not Apply', genus: 'X', species: 'y',
  }]);
  const out = applyMarksToValues(values, marks);
  assert.strictEqual(out[1][SALES_HEADER.indexOf('sync_status')], 'Synced');
  assert.strictEqual(out[1][SALES_HEADER.indexOf('name')], 'Acacia');
  assert.strictEqual(out[1][SALES_HEADER.indexOf('genus')], '');
});

test('applyMarksToValues treats name unknown case-insensitively', () => {
  const values = [
    SALES_HEADER,
    salesRow('PP-1-1', '2026-06-23', 1, '31011', '  UNKNOWN  ', 2, 'pots', 500, 0, 1000, 'Pending'),
  ];
  const marks = resolveSalesMarks(values, [{
    receipt: 'PP-1-1', item_seq: 1, status: 'Synced', name: 'Resolved',
  }]);
  const out = applyMarksToValues(values, marks);
  assert.strictEqual(out[1][SALES_HEADER.indexOf('name')], 'Resolved');
});

test('applyMarksToValues is backward compatible without enrichment fields', () => {
  const values = [
    SALES_HEADER,
    salesRow('PP-1-1', '2026-06-23', 1, '31011', 'unknown', 2, 'pots', 500, 0, 1000, 'Pending'),
  ];
  const marks = resolveSalesMarks(values, [{ receipt: 'PP-1-1', item_seq: 1, status: 'Synced' }]);
  const out = applyMarksToValues(values, marks);
  assert.strictEqual(out[1][SALES_HEADER.indexOf('sync_status')], 'Synced');
  assert.strictEqual(out[1][SALES_HEADER.indexOf('name')], 'unknown');
});

test('applyMarksToValues enriches unknown cull rows', () => {
  const values = [
    CULLS_SHEET_HEADER,
    ['PP-1-1', '2026-07-01', '31011', 'unknown', '', '', '', '', '', 1, 'tubes', 'Dead', '', 'Pending'],
  ];
  const marks = resolveCullMarks(values, [{
    cull_id: 'PP-1-1', status: 'Synced',
    name: 'Acacia pycnantha', genus: 'Acacia', species: 'pycnantha', group: 'Tree',
  }]);
  const out = applyMarksToValues(values, marks);
  assert.strictEqual(out[1][CULLS_SHEET_HEADER.indexOf('sync_status')], 'Synced');
  assert.strictEqual(out[1][CULLS_SHEET_HEADER.indexOf('name')], 'Acacia pycnantha');
  assert.strictEqual(out[1][CULLS_SHEET_HEADER.indexOf('genus')], 'Acacia');
  assert.strictEqual(out[1][CULLS_SHEET_HEADER.indexOf('group')], 'Tree');
});

const CULLS_HEADER = [
  'cull_id', 'date', 'accession', 'name', 'genus', 'species', 'cultivar', 'common_name',
  'group', 'qty', 'unit', 'reason', 'notes',
];

test('ensureSyncStatusColumn appends sync_status when missing', () => {
  assert.deepStrictEqual(
    ensureSyncStatusColumn(CULLS_HEADER),
    [...CULLS_HEADER, 'sync_status'],
  );
});

test('ensureSyncStatusColumn does not duplicate an existing sync_status column', () => {
  const withStatus = [...CULLS_HEADER, 'sync_status'];
  assert.deepStrictEqual(ensureSyncStatusColumn(withStatus), withStatus);
  const midStatus = ['cull_id', 'sync_status', 'date'];
  assert.deepStrictEqual(ensureSyncStatusColumn(midStatus), midStatus);
});

// ---- Reverse sync (Culls -> Access) selection & marking -------------------------------------------
const CULLS_SHEET_HEADER = ensureSyncStatusColumn(CULLS_HEADER);

test('selectPendingCulls returns [] for an empty or header-only sheet', () => {
  assert.deepStrictEqual(selectPendingCulls(null), []);
  assert.deepStrictEqual(selectPendingCulls([]), []);
  assert.deepStrictEqual(selectPendingCulls([CULLS_SHEET_HEADER]), []);
});

test('selectPendingCulls selects only Pending rows, shaped {cull_id,accession,qty,unit,notes,name}', () => {
  const values = [
    CULLS_SHEET_HEADER,
    ['PP-1-1', '2026-07-01', '31011', 'Acacia', '', '', '', '', 'Tree', 2, 'tubes', 'Dead', '', 'Pending'],
    ['PP-1-2', '2026-07-01', '8250', 'Banksia', '', '', '', '', '', 1, 'pots', 'Pest', 'aphids', 'Synced'],
    ['PP-1-3', '2026-07-01', '9000', 'Grevillea', '', '', '', '', '', 1, 'misc', 'Other', '', 'Pending'],
  ];
  assert.deepStrictEqual(selectPendingCulls(values), [
    { cull_id: 'PP-1-1', accession: '31011', qty: 2, unit: 'tubes', notes: '', name: 'Acacia' },
    { cull_id: 'PP-1-3', accession: '9000', qty: 1, unit: 'misc', notes: '', name: 'Grevillea' },
  ]);
});

test('selectPendingCulls carries notes for stock-plant routing', () => {
  const values = [
    CULLS_SHEET_HEADER,
    ['PP-2-1', '2026-07-01', '16726', 'Hardenbergia', '', '', '', '', '', 1, 'pots', 'Dead', 'Stock plant', 'Pending'],
  ];
  assert.deepStrictEqual(selectPendingCulls(values), [
    { cull_id: 'PP-2-1', accession: '16726', qty: 1, unit: 'pots', notes: 'Stock plant', name: 'Hardenbergia' },
  ]);
});

test('selectPendingCulls carries unknown name for Access enrichment gating', () => {
  const values = [
    CULLS_SHEET_HEADER,
    ['PP-3-1', '2026-07-01', '31011', 'unknown', '', '', '', '', '', 1, 'tubes', 'Dead', '', 'Pending'],
  ];
  assert.deepStrictEqual(selectPendingCulls(values), [
    { cull_id: 'PP-3-1', accession: '31011', qty: 1, unit: 'tubes', notes: '', name: 'unknown' },
  ]);
});

test('resolveCullMarks maps each cull_id key to its values row index and status', () => {
  const values = [
    CULLS_SHEET_HEADER,
    ['PP-1-1', '2026-07-01', '31011', 'A', '', '', '', '', '', 1, 'tubes', 'Dead', '', 'Pending'],
    ['PP-1-2', '2026-07-01', '8250', 'B', '', '', '', '', '', 1, 'pots', 'Dead', '', 'Pending'],
  ];
  assert.deepStrictEqual(
    resolveCullMarks(values, [{ cull_id: 'PP-1-2', status: 'Synced' }]),
    [{ rowIndex: 2, status: 'Synced' }],
  );
});

test('resolveCullMarks ignores keys with no matching row (not an error)', () => {
  const values = [CULLS_SHEET_HEADER, ['PP-1-1', '2026-07-01', '31011', 'A', '', '', '', '', '', 1, 'tubes', 'Dead', '', 'Pending']];
  assert.deepStrictEqual(
    resolveCullMarks(values, [
      { cull_id: 'PP-1-1', status: 'Synced' },
      { cull_id: 'PP-NOPE', status: 'Synced' },
    ]),
    [{ rowIndex: 1, status: 'Synced' }],
  );
});

test('isStockPlantCull matches the spec phrase case-insensitively after trim', () => {
  assert.strictEqual(isStockPlantCull('Stock plant', 'pots'), true);
  assert.strictEqual(isStockPlantCull('  stock plant  ', 'pot'), true);
  assert.strictEqual(isStockPlantCull('STOCK PLANT', 'POTS'), true);
  assert.strictEqual(isStockPlantCull('Stock plant', 'tubes'), false);
  assert.strictEqual(isStockPlantCull('Stock plant cull', 'pots'), false);
  assert.strictEqual(isStockPlantCull('', 'pots'), false);
  assert.strictEqual(isStockPlantCull(null, 'pots'), false);
});

test('computeCullDeduction subtracts only from the named container, clamped at zero', () => {
  // Issue example: Tubes=4, cull 1 Pot at Pots=0 -> Pots stays 0, Tubes untouched
  assert.deepStrictEqual(
    computeCullDeduction('pots', 1, 0, 4, 0),
    { pots: 0, tubes: 4, misc: 0 },
  );
  assert.deepStrictEqual(
    computeCullDeduction('tubes', 3, 10, 5, 4),
    { pots: 10, tubes: 2, misc: 4 },
  );
  assert.deepStrictEqual(
    computeCullDeduction('misc', 7, 10, 5, 4),
    { pots: 10, tubes: 5, misc: 0 }, // no misc->pots overflow (unlike sales)
  );
  assert.deepStrictEqual(
    computeCullDeduction('pots', 9, 2, 5, 4),
    { pots: 0, tubes: 5, misc: 4 },
  );
});

test('validateAppendCullsNotes accepts clean notes and empty notes', () => {
  const rows = [
    ['PP-1-1', '2026-07-01', '31011', 'Acacia', '', '', '', '', 'Tree', 2, 'tubes', 'Dead', 'aphids on tips'],
    ['PP-1-2', '2026-07-01', '8250', 'Banksia', '', '', '', '', '', 1, 'pots', 'Dead', ''],
  ];
  assert.strictEqual(validateAppendCullsNotes(CULLS_HEADER, rows), null);
});

test('validateAppendCullsNotes rejects notes containing bracket or brace characters', () => {
  const rows = [
    ['PP-1-1', '2026-07-01', '31011', 'Acacia', '', '', '', '', 'Tree', 2, 'tubes', 'Dead', 'ok'],
    ['PP-1-2', '2026-07-01', '8250', 'Banksia', '', '', '', '', '', 1, 'pots', 'Dead', 'bad [note'],
  ];
  assert.strictEqual(
    validateAppendCullsNotes(CULLS_HEADER, rows),
    'Cull notes contain unsupported characters',
  );
  for (const ch of ['[', ']', '{', '}']) {
    const bad = [
      ['PP-1-1', '2026-07-01', '31011', 'Acacia', '', '', '', '', 'Tree', 2, 'tubes', 'Dead', `x${ch}y`],
    ];
    assert.strictEqual(
      validateAppendCullsNotes(CULLS_HEADER, bad),
      'Cull notes contain unsupported characters',
    );
  }
});

test('validateAppendCullsNotes resolves notes column from header order', () => {
  const header = ['notes', 'cull_id', 'date'];
  const rows = [['fine', 'PP-1-1', '2026-07-01']];
  assert.strictEqual(validateAppendCullsNotes(header, rows), null);
  rows[0][0] = '{bad}';
  assert.strictEqual(
    validateAppendCullsNotes(header, rows),
    'Cull notes contain unsupported characters',
  );
});

test('filterNewRows dedupes culls by cull_id independently', () => {
  const incoming = [
    ['PP-1-1', '2026-07-01', '31011'],
    ['PP-1-2', '2026-07-01', '31011'], // same accession, different cull_id -> both kept
    ['PP-1-3', '2026-07-01', '8250'],  // already exported -> skipped
  ];
  const existing = ['PP-1-3'];
  const result = filterNewRows(incoming, existing, 0);
  assert.strictEqual(result.skipped, 1);
  assert.strictEqual(result.rows.length, 2);
  assert.deepStrictEqual(result.rows.map((r) => r[0]), ['PP-1-1', 'PP-1-2']);
});

// ---- Reverse sync (PrintQueue -> Access) selection & marking --------------------------------------
// Mirrors LabelPrintExport.HEADER in core/ (+ sheet-only sync_status).
const PRINT_LABELS_HEADER = [
  'queue_id', 'date', 'accession', 'name', 'copies',
];
const PRINT_LABELS_SHEET_HEADER = ensureSyncStatusColumn(PRINT_LABELS_HEADER);

test('ensureSyncStatusColumn appends sync_status for PrintQueue header', () => {
  assert.deepStrictEqual(
    ensureSyncStatusColumn(PRINT_LABELS_HEADER),
    [...PRINT_LABELS_HEADER, 'sync_status'],
  );
});

test('selectPendingPrintLabels returns [] for an empty or header-only sheet', () => {
  assert.deepStrictEqual(selectPendingPrintLabels(null), []);
  assert.deepStrictEqual(selectPendingPrintLabels([]), []);
  assert.deepStrictEqual(selectPendingPrintLabels([PRINT_LABELS_SHEET_HEADER]), []);
});

test('selectPendingPrintLabels selects only Pending rows, shaped {queue_id,date,accession,name,copies}', () => {
  const values = [
    PRINT_LABELS_SHEET_HEADER,
    ['07-1-1', '2026-07-01T12:00', '31011', 'Acacia', 2, 'Pending'],
    ['07-1-2', '2026-07-01T12:00', '8250', 'Banksia', 1, 'Synced'],
    ['07-1-3', '2026-07-01T13:00', '9000', 'Grevillea', 3, 'Pending'],
  ];
  assert.deepStrictEqual(selectPendingPrintLabels(values), [
    { queue_id: '07-1-1', date: '2026-07-01T12:00', accession: '31011', name: 'Acacia', copies: 2 },
    { queue_id: '07-1-3', date: '2026-07-01T13:00', accession: '9000', name: 'Grevillea', copies: 3 },
  ]);
});

test('resolvePrintLabelMarks maps each queue_id key to its values row index and status', () => {
  const values = [
    PRINT_LABELS_SHEET_HEADER,
    ['07-1-1', '2026-07-01T12:00', '31011', 'A', 1, 'Pending'],
    ['07-1-2', '2026-07-01T12:00', '8250', 'B', 2, 'Pending'],
  ];
  assert.deepStrictEqual(
    resolvePrintLabelMarks(values, [{ queue_id: '07-1-2', status: 'Synced' }]),
    [{ rowIndex: 2, status: 'Synced' }],
  );
});

test('resolvePrintLabelMarks ignores keys with no matching row (not an error)', () => {
  const values = [
    PRINT_LABELS_SHEET_HEADER,
    ['07-1-1', '2026-07-01T12:00', '31011', 'A', 1, 'Pending'],
  ];
  assert.deepStrictEqual(
    resolvePrintLabelMarks(values, [
      { queue_id: '07-1-1', status: 'Synced' },
      { queue_id: '07-NOPE', status: 'Synced' },
    ]),
    [{ rowIndex: 1, status: 'Synced' }],
  );
});

test('filterNewRows dedupes print labels by queue_id', () => {
  const incoming = [
    ['07-1-1', '2026-07-01T12:00', '31011'],
    ['07-1-2', '2026-07-01T12:00', '31011'],
    ['07-1-3', '2026-07-01T12:00', '8250'],
  ];
  const existing = ['07-1-3'];
  const result = filterNewRows(incoming, existing, 0);
  assert.strictEqual(result.skipped, 1);
  assert.strictEqual(result.rows.length, 2);
  assert.deepStrictEqual(result.rows.map((r) => r[0]), ['07-1-1', '07-1-2']);
});

test('applyMarksToValues flips PrintQueue sync_status without plant enrichment', () => {
  const values = [
    PRINT_LABELS_SHEET_HEADER,
    ['07-1-1', '2026-07-01T12:00', '31011', 'Acacia', 2, 'Pending'],
  ];
  const marked = applyMarksToValues(values, [{ rowIndex: 1, status: 'Synced' }]);
  assert.strictEqual(marked[1][5], 'Synced');
  assert.strictEqual(marked[1][3], 'Acacia');
});

test('validateAppendPrintLabelCopies accepts integer copies from 1 to max', () => {
  const rows = [
    ['07-1-1', '2026-07-01T12:00', '31011', 'Acacia', 1],
    ['07-1-2', '2026-07-01T12:00', '8250', 'Banksia', PRINT_LABEL_COPIES_MAX],
  ];
  assert.strictEqual(validateAppendPrintLabelCopies(PRINT_LABELS_HEADER, rows), null);
});

test('validateAppendPrintLabelCopies rejects non-positive, non-integer, or extreme copies', () => {
  const msg = 'Print label copies must be an integer from 1 to ' + PRINT_LABEL_COPIES_MAX;
  assert.strictEqual(
    validateAppendPrintLabelCopies(PRINT_LABELS_HEADER, [['07-1', '2026-07-01T12:00', '31011', 'A', 0]]),
    msg,
  );
  assert.strictEqual(
    validateAppendPrintLabelCopies(PRINT_LABELS_HEADER, [['07-1', '2026-07-01T12:00', '31011', 'A', -3]]),
    msg,
  );
  assert.strictEqual(
    validateAppendPrintLabelCopies(PRINT_LABELS_HEADER, [['07-1', '2026-07-01T12:00', '31011', 'A', 1.5]]),
    msg,
  );
  assert.strictEqual(
    validateAppendPrintLabelCopies(PRINT_LABELS_HEADER, [['07-1', '2026-07-01T12:00', '31011', 'A', PRINT_LABEL_COPIES_MAX + 1]]),
    msg,
  );
  assert.strictEqual(
    validateAppendPrintLabelCopies(PRINT_LABELS_HEADER, [['07-1', '2026-07-01T12:00', '31011', 'A', 'abc']]),
    msg,
  );
});

// ---- Stock prediction on append (#80) — mirrors Access DeductSelfTest / ComputeDeduction_ ---------
test('computeSalesDeduction subtracts pots only, clamped at zero', () => {
  assert.deepStrictEqual(
    computeSalesDeduction('pots', 3, 10, 5, 4),
    { pots: 7, tubes: 5, misc: 4 },
  );
  assert.deepStrictEqual(
    computeSalesDeduction('pots', 9, 2, 5, 4),
    { pots: 0, tubes: 5, misc: 4 },
  );
});

test('computeSalesDeduction subtracts tubes only, clamped at zero', () => {
  assert.deepStrictEqual(
    computeSalesDeduction('tubes', 3, 10, 5, 4),
    { pots: 10, tubes: 2, misc: 4 },
  );
  assert.deepStrictEqual(
    computeSalesDeduction('tubes', 9, 10, 2, 4),
    { pots: 10, tubes: 0, misc: 4 },
  );
});

test('computeSalesDeduction misc overflows into pots when oversold', () => {
  // DeductSelfTest cases: pure misc, exact zero, partial overflow, overflow exceeding pots
  assert.deepStrictEqual(
    computeSalesDeduction('misc', 3, 10, 5, 4),
    { pots: 10, tubes: 5, misc: 1 },
  );
  assert.deepStrictEqual(
    computeSalesDeduction('misc', 4, 10, 5, 4),
    { pots: 10, tubes: 5, misc: 0 },
  );
  assert.deepStrictEqual(
    computeSalesDeduction('misc', 7, 10, 5, 4),
    { pots: 7, tubes: 5, misc: 0 },
  );
  assert.deepStrictEqual(
    computeSalesDeduction('misc', 9, 2, 5, 4),
    { pots: 0, tubes: 5, misc: 0 },
  );
});

test('computeSalesDeduction blank or unrecognized unit moves nothing', () => {
  assert.deepStrictEqual(
    computeSalesDeduction('', 5, 10, 5, 4),
    { pots: 10, tubes: 5, misc: 4 },
  );
  assert.deepStrictEqual(
    computeSalesDeduction('unknown', 5, 10, 5, 4),
    { pots: 10, tubes: 5, misc: 4 },
  );
});

test('computeSalesDeduction successive misc rows are order-independent', () => {
  // DeductSelfTest PrintOrderIndependence_: misc 3 then 5 vs 5 then 3 from (4,0,6)
  let a = { pots: 4, tubes: 0, misc: 6 };
  a = computeSalesDeduction('misc', 3, a.pots, a.tubes, a.misc);
  a = computeSalesDeduction('misc', 5, a.pots, a.tubes, a.misc);
  let b = { pots: 4, tubes: 0, misc: 6 };
  b = computeSalesDeduction('misc', 5, b.pots, b.tubes, b.misc);
  b = computeSalesDeduction('misc', 3, b.pots, b.tubes, b.misc);
  assert.deepStrictEqual(a, b);
});

test('predictStockUpdates deducts a sale from the matching Plants-tab accession', () => {
  const plants = [
    ['Ac Number', 'PotsInNursery', 'TubesInNursery', 'MiscInNursery', 'StockInNursery'],
    ['31011', 10, 5, 4, 2],
    ['8250', 3, 0, 0, 1],
  ];
  const appended = [
    salesRow('PP-1-1', '2026-07-11', 1, '31011', 'Acacia', 3, 'pots', 500, 0, 1500, 'Pending'),
  ];
  assert.deepStrictEqual(
    predictStockUpdates(plants, SALES_HEADER, appended, 'sales'),
    [{ rowIndex: 1, pots: 7, tubes: 5, misc: 4 }],
  );
  // StockInNursery on the plants sheet is untouched (not part of the update payload)
  assert.strictEqual(plants[1][4], 2);
});

test('predictStockUpdates skips unknown accessions and applies misc→pots overflow', () => {
  const plants = [
    ['Ac Number', 'PotsInNursery', 'TubesInNursery', 'MiscInNursery', 'StockInNursery'],
    ['31011', 10, 5, 4, 2],
  ];
  const appended = [
    salesRow('PP-1-1', '2026-07-11', 1, '99999', 'unknown', 2, 'pots', 500, 0, 1000, 'Pending'),
    salesRow('PP-1-1', '2026-07-11', 2, '31011', 'Acacia', 7, 'misc', 100, 0, 700, 'Pending'),
  ];
  assert.deepStrictEqual(
    predictStockUpdates(plants, SALES_HEADER, appended, 'sales'),
    [{ rowIndex: 1, pots: 7, tubes: 5, misc: 0 }],
  );
});

test('predictStockUpdates deducts a cull from only the named pot type', () => {
  const plants = [
    ['Ac Number', 'PotsInNursery', 'TubesInNursery', 'MiscInNursery', 'StockInNursery'],
    ['31011', 10, 5, 4, 0],
  ];
  const appended = [
    ['PP-1-1', '2026-07-11', '31011', 'Acacia', '', '', '', '', 'Tree', 3, 'tubes', 'Dead', ''],
  ];
  // Culls: only tubes drop; misc oversell would NOT overflow to pots (unlike sales)
  assert.deepStrictEqual(
    predictStockUpdates(plants, CULLS_HEADER, appended, 'culls'),
    [{ rowIndex: 1, pots: 10, tubes: 2, misc: 4 }],
  );
});

test('predictStockUpdates skips stock-plant culls and leaves zeroed plants on the tab', () => {
  const plants = [
    ['Ac Number', 'PotsInNursery', 'TubesInNursery', 'MiscInNursery', 'StockInNursery'],
    ['31011', 2, 0, 0, 1],
    ['8250', 1, 0, 0, 0],
  ];
  const appended = [
    // Stock plant: notes + pots → no stock move
    ['PP-1-1', '2026-07-11', '31011', 'Acacia', '', '', '', '', '', 1, 'pots', 'Dead', 'Stock plant'],
    // Regular cull that zeros the plant — plant stays with zeros
    ['PP-1-2', '2026-07-11', '8250', 'Banksia', '', '', '', '', '', 1, 'pots', 'Dead', ''],
  ];
  assert.deepStrictEqual(
    predictStockUpdates(plants, CULLS_HEADER, appended, 'culls'),
    [{ rowIndex: 2, pots: 0, tubes: 0, misc: 0 }],
  );
});

test('predictStockUpdates returns [] when nothing was appended (dedupe-only push)', () => {
  const plants = [
    ['Ac Number', 'PotsInNursery', 'TubesInNursery', 'MiscInNursery', 'StockInNursery'],
    ['31011', 10, 5, 4, 2],
  ];
  assert.deepStrictEqual(predictStockUpdates(plants, SALES_HEADER, [], 'sales'), []);
});
