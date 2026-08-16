# backend/

Google Apps Script web app. `Code.gs` is the `doGet`/`doPost` entry point.

## Commands

```bash
node --test backend/test/logic.test.js
node --test --test-name-pattern "isAuthorized" backend/test/logic.test.js
```

Deploy: [`docs/deploy/backend.md`](../docs/deploy/backend.md).

## Source layout

- **`shared.js`** — pure logic (auth, plant parsing, dedupe). Source of truth; mirrored into the GAS
  project as `shared.gs`.
- **`Code.gs`** — HTTP handlers. `plantListSync` is the device cloud-sync action (queues + plant
  list in one POST). Compat actions remain for old apps (`appendSales`, `appendCulls`,
  `appendPrintLabels`, `appendRepots`, `getPlants`) plus matching `pending*` / `mark*Synced`
  reverse-sync pairs.

## Rules

- Change logic in `shared.js` first; run `node --test` before deploy.
- Export column order comes from `Export.HEADER`, `CullExport.HEADER`, `LabelPrintExport.HEADER`,
  and `RepotExport.HEADER` in `core/` — copied as `SALES_EXPORT_HEADER` / `CULLS_EXPORT_HEADER` /
  `PRINT_LABELS_EXPORT_HEADER` / `REPOTS_EXPORT_HEADER` in `shared.js`. Never reorder columns without
  updating both sides. `plantListSync` sends rows only (no `header`).
- Sheet tabs and `sync_status` behaviour: see `Code.gs` and tests.

See root [`AGENTS.md`](../AGENTS.md) for sync invariants.
