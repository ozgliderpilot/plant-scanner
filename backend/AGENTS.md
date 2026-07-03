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
- **`Code.gs`** — HTTP handlers. Separate actions for sales and culls (`appendSales`, `appendCulls`).

## Rules

- Change logic in `shared.js` first; run `node --test` before deploy.
- Export column order comes from `Export.HEADER` and `CullExport.HEADER` in `core/` — keep backend
  handlers aligned; never reorder columns without updating both sides.
- Sheet tabs and `sync_status` behaviour: see `Code.gs` and tests.

See root [`AGENTS.md`](../AGENTS.md) for sync invariants.
