# Access → Google Sheets Plant Sync — Design

**Date:** 2026-06-15
**Status:** Built & validated (dry-run + live POST on the nursery PC). Deployed via `docs/deploy/access.md`.
**Related:** `docs/deploy/access.md` (setup), `docs/deploy/backend.md` (web app), `backend/` (implementation)

## Problem

The nursery's plant catalogue and live stock live in a Microsoft Access database (`GFRBG.mdb`
front-end + `GFRBG Database Data.mdb` back-end) on a Windows 10 PC at the nursery. The Android selling
app reads its plant list from a Google Sheet (`Plants` tab) via the existing Apps Script web app. We
need the in-stock plants (with their stock counts and descriptive attributes) to flow automatically
from Access into that Sheet, without manual export, and without copies of the database taken home by
volunteers ever writing to the live Sheet.

## Architecture

```
Access front-end (GFRBG.mdb, nursery PC)
   │  VBA Form Timer (every ~10 min while open) — modPlantSync.SyncPlants
   │  reads qryPlantsExport, builds JSON, POSTs
   ▼  HTTPS POST { action:"replacePlants", secret, header, rows }
Apps Script Web App (/exec)  ──►  Google Sheet
   ├─ replacePlants → full-mirror rewrite of the "Plants" tab          (from Access)
   ├─ getPlants     → returns plant objects to the Android app          (to device)
   ├─ appendSales   → appends sales rows, deduped by receipt #          (from device)
   └─ every action stamps the "SyncStatus" tab (last-sync log)
```

The Apps Script web app **is** the integration endpoint: it runs inside Google with native
`SpreadsheetApp` access, so the nursery PC needs no Google credentials, OAuth, or ODBC bitness
handling — just an HTTPS POST with a shared secret. This was chosen over a direct Google Sheets API
client on the PC or a separate serverless function precisely because the web app already existed
(Phase 1) and removes all credential/bitness concerns from the PC.

## Source data model (Access)

- **`Batches`** (23,947 rows) — one row per **accession**. `Ac Number` is the accession/barcode
  (unique; it is what the Android app scans). Each batch links to a species via `Id No`, and carries
  the per-accession stock: `PotsInNursery`, `TubesInNursery`, `MiscInNursery`, `StockInNursery` and the
  `PotsForSale` / `TubesForSale` / `MiscForSale` flags. **Stock is per-accession on this row — no
  aggregation needed.**
- **`Species`** (5,201 rows) — the plant master (genus/species/cultivar, plant type, sun/shade,
  tolerances, dimensions, etc.), keyed by `Id No`. A species can span many batches (up to 75).
- The other ~20 tables are single-column lookups and nursery-ops tables (`Genus`, `Soil Types`,
  `PrintQueue`, …) — not synced.

Key fact: **accession == `Batches.[Ac Number]`**, not `Species.[Id No]`. The datasheet shows column
*captions* (`Ac No.`, `Species Id No`) that differ from the real field *names* (`Ac Number`, `Id No`);
SQL and the sync use the field names.

## The `Plants` tab (export contract)

A **raw view of `Batches` ⋈ `Species` on `Id No`**, one row per accession, **all 43 columns** (18 from
Batches + 25 from Species; `Id No` appears once), filtered to in-stock accessions:

```
Nz(PotsInNursery,0) + Nz(TubesInNursery,0) + Nz(MiscInNursery,0) + Nz(StockInNursery,0) > 0
```

This yields **~1,777 rows** (verified against live data). Column order is the Batches columns first,
then the Species columns. The header row uses the **raw Access field names** (`Ac Number`, `Genus`,
`Sun/Shade`, …). The query is `qryPlantsExport`, embedded in the VBA as `PLANTS_SQL` and auto-created
on first run (single source of truth — there is no separate `.sql` file).

## Sync-semantics decisions (and why)

- **Full mirror, not upsert.** Each push clears the `Plants` tab and rewrites exactly the current
  in-stock set. Because stock is volatile, a sold-out accession simply isn't in the next push and
  drops off automatically; counts never go stale. (Upsert was considered but rejected once the sync
  became stock-gated: it would leave sold-out plants lingering with stale stock.)
- **Stock-gated.** Only accessions with stock > 0 are pushed. The Android app then only sees in-stock
  plants; a scan of an accession not in the list still works — it becomes a "sell as unknown" line per
  `PlantBook`.
- **Raw column names, app stays compatible.** Rather than aliasing columns, the Sheet carries the raw
  Access names and the backend's `parsePlants` was updated to read them (accession ← `Ac Number`,
  group ← `Plant Type`, light ← `Sun/Shade`, name composed from `Genus` + `Species` + `Cultivar`,
  falling back to `Common Name`). So `getPlants` still returns the same `{accession,name,group,light}`
  objects and the Android/Kotlin layer is untouched. Legacy `accession/name/group/light` headers still
  parse, for a clean migration.

## Backend (Apps Script) — `backend/Code.gs`, `backend/shared.js`

Actions on `doPost`, all shared-secret authorised:

- **`getPlants`** — reads `Plants`, returns plant objects (the app's "Update plant list"). Logs
  *Plant list to device*.
- **`replacePlants`** — full-mirror rewrite from Access. Acquires the document lock, `clearContents()`,
  writes header + rows, force-texts the accession column so identifiers aren't coerced. Logs *Plants
  from Access*.
- **`appendSales`** — appends sales rows, deduped by receipt # (unchanged Phase-1 behaviour). Logs
  *Sales from device*.

**`SyncStatus` tab** — `recordSync_` upserts one row per event (`Event`, `Direction`, `Last Sync`,
`Detail`), so the Sheet always shows the last time each sync ran. It is wrapped in try/catch so a
logging hiccup can never fail a real sync; the row-matching (`findRowByKey`) lives in `shared.js` and
is unit-tested.

**Pure, unit-tested logic in `shared.js`** (per the project rule that error-prone logic is testable
without the Android SDK): `parsePlants` (raw-header aware + name composition), `planPlantReplace`
(skip blank accession, dedupe by accession keyed on `accession`/`ac number`, header pass-through),
`findRowByKey`, plus existing `isAuthorized` / `filterNewRows`. Covered by `backend/test/logic.test.js`
(`node --test`).

## Access client — `backend/access/modPlantSync.bas`

- **Trigger: in-app Form Timer (Option A).** A form that stays open during nursery hours sets
  `TimerInterval = 600000` (10 min) and calls `modPlantSync.SyncPlants` on `Form_Timer`; an optional
  "Sync now" button calls `SyncPlants True, True`. This mirrors the app's own `AutoExportTicker`
  (in-app ticker, not WorkManager) — it runs only while the DB is open, which is exactly when stock
  changes, and needs no Windows Task Scheduler or always-on PC.
- **Config via environment variables (home-copy safeguard).** `WEB_APP_URL` and the shared secret are
  read at run time from Windows user env vars `GFRBG_SYNC_URL` / `GFRBG_SYNC_SECRET` — **not** stored
  in the database. If either is missing, `SyncPlants` exits immediately and never contacts Google. So
  only the nursery PC (where they're set via `setx`) syncs; copies taken home are inert with no code
  differences. `SyncSelfTest` reports each var SET/MISSING without printing the secret.
- **Change-detection (cheap skip).** Each tick builds the exact payload and hashes it; if the hash
  matches the last **successful** push (stored via `SaveSetting`), the POST is skipped — an idle
  nursery makes no cloud calls. The hash is saved **only on a confirmed `ok` response**, so a failed
  push retries next tick (the analog of the app flipping rows to `EXPORTED` on success).
- **Robustness.** A re-entrancy guard (`mPushing`) stops a slow POST overlapping the next tick (analog
  of the backend `cloudMutex`); the timer path swallows all errors (no volunteer popups); the manual
  button can show them. Transport is `MSXML2.ServerXMLHTTP.6.0` with short timeouts, following the
  Apps Script 302 redirect. The export query auto-creates on first run (`EnsureQueryDef_`).

## Validation performed (2026-06-15, nursery PC)

- **Filter / field names:** `DCount` over `Batches` with the stock filter returned **1777** — matches
  the offline analysis and confirms the stock field names.
- **Query:** `qryPlantsExport` returned **1777 rows, 43 columns**, field 0 = `Ac Number`, field 28 =
  `Sun/Shade` — the exact raw names `parsePlants` maps.
- **Dry run:** `SyncSelfTest` built a well-formed ~833 KB payload with correct JSON typing (unquoted
  numbers, `null`, `false`, quoted dates/text).
- **Live POST:** `SyncPlants True, True` succeeded — the web app accepted `replacePlants` and rewrote
  the `Plants` tab; success confirmed via the saved hash (saved only on `ok`). This also confirmed the
  updated backend is deployed.
- Backend unit tests green (`node --test backend/test/logic.test.js`).

## Deployment

See `docs/deploy/access.md`: import `modPlantSync.bas`, set the two env vars, wire the Form Timer (+
optional button), Trusted Location. The query auto-creates; the `SyncStatus` tab appears on first sync.
Requires the updated `Code.gs` + `shared.gs` deployed to the web app.

## Open items / future

- **Secret storage.** Env vars are readable by the nursery Windows user (not a vault). Adequate for the
  home-copy goal; a per-machine config file with tighter permissions is an option if needed.
- **Client-side timestamp.** The `SyncStatus` stamp is server-side ("when the Sheet received"). A
  client `lastSyncedAt` ("when Access last attempted") could be added if useful.
- **Deletions.** Full mirror handles sold-out drop-off; there is intentionally no soft-delete/audit of
  removed accessions.
