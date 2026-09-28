/**
 * Ops-budget regression for getPlants latency.
 *
 * The ticker hits plantListSync (or getPlants on old apps) often (default 60s). SpreadsheetApp + LockService calls dominate
 * Apps Script wall time, so the unchanged (fingerprint match) path must not pay for a second
 * exclusive document lock beyond the handler's own withDocumentLock_.
 *
 * Magic-link device claim (ADR-0017) originally wrapped Users-tab auth in its own waitLock
 * before handleGetPlants_ locked again — two acquires per pull. Auth must run inside the
 * handler lock instead.
 */
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const codePath = path.join(__dirname, '..', 'Code.gs');
const code = fs.readFileSync(codePath, 'utf8');

function functionBody(name) {
  const re = new RegExp('function ' + name + '\\([^)]*\\)\\s*\\{');
  const start = code.search(re);
  assert.ok(start >= 0, 'missing function ' + name);
  let i = code.indexOf('{', start);
  let depth = 0;
  for (; i < code.length; i++) {
    const ch = code[i];
    if (ch === '{') depth++;
    else if (ch === '}') {
      depth--;
      if (depth === 0) return code.slice(start, i + 1);
    }
  }
  assert.fail('unbalanced braces in ' + name);
}

test('authorizeDeviceRequest_ must not take its own document lock', () => {
  const body = functionBody('authorizeDeviceRequest_');
  assert.doesNotMatch(
    body,
    /LockService\.getDocumentLock|waitLock\s*\(/,
    'authorizeDeviceRequest_ must rely on the caller holding withDocumentLock_',
  );
});

test('doPost must not call device auth before handlers', () => {
  // Strip // comments so prose in doPost cannot false-trigger the call check.
  const body = functionBody('doPost').replace(/\/\/.*$/gm, '');
  assert.doesNotMatch(body, /\bauthorizeDeviceRequest_\s*\(|\brequireDeviceAuthorization_\s*\(/);
});

test('handleGetPlants_ device-auths inside its single document lock', () => {
  const body = functionBody('handleGetPlants_');
  assert.match(body, /withDocumentLock_/);
  assert.match(body, /requireDeviceAuthorization_/);
});

test('handleAppendExport_ device-auths inside its single document lock', () => {
  const body = functionBody('handleAppendExport_');
  assert.match(body, /withDocumentLock_/);
  assert.match(body, /requireDeviceAuthorization_/);
});

test('handlePlantListSync_ device-auths inside its single document lock', () => {
  const body = functionBody('handlePlantListSync_');
  assert.match(body, /withDocumentLock_/);
  assert.match(body, /requireDeviceAuthorization_/);
  assert.doesNotMatch(body, /handleAppendSales_|handleGetPlants_/);
});
