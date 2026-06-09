const test = require('node:test');
const assert = require('node:assert');
const { isAuthorized, emptyToNull, parsePlants, filterNewRows } = require('../shared.js');

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
