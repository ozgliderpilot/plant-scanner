const test = require('node:test');
const assert = require('node:assert');
const { isAuthorized, emptyToNull, parsePlants, filterNewRows, planPlantReplace, findRowByKey, accessionColIndex } = require('../shared.js');

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
    accession: '2021-0345', name: 'Banksia', group: 'Proteaceae', light: 'Full sun',
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

test('findRowByKey returns the data-row index of the matching event, else -1', () => {
  const keys = [['Plants from Access'], ['Sales from device']];
  assert.strictEqual(findRowByKey(keys, 'Plants from Access'), 0);
  assert.strictEqual(findRowByKey(keys, 'Sales from device'), 1);
  assert.strictEqual(findRowByKey(keys, 'Plant list to device'), -1);
  assert.strictEqual(findRowByKey([], 'anything'), -1);
  assert.strictEqual(findRowByKey(null, 'anything'), -1);
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
    accession: '31011', name: 'Acacia pycnantha', group: 'Tree', light: 'Full sun',
  });
  // cultivar folded into the name
  assert.strictEqual(plants[1].name, "Banksia integrifolia 'Roller Coaster'");
  assert.strictEqual(plants[1].group, 'Shrub');
});

test('parsePlants name falls back to Common Name, and legacy headers still work', () => {
  assert.strictEqual(
    parsePlants([['Ac Number', 'Genus', 'Common Name'], ['9', '', 'Mystery Plant']])[0].name,
    'Mystery Plant',
  );
  // old-style sheet (accession/name/group/light) keeps parsing
  const legacy = parsePlants([['accession', 'name', 'group', 'light'], ['2021-1', 'Wattle', 'Tree', 'Sun']]);
  assert.deepStrictEqual(legacy[0], { accession: '2021-1', name: 'Wattle', group: 'Tree', light: 'Sun' });
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
