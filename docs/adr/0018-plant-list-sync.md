# One `plantListSync` POST for cloud sync

## Context

Cloud sync is one device↔Sheets round trip (ADR-0007): export the sync queue, then import the plant
list. The device still made up to five POSTs (`appendSales` → `appendCulls` → `appendPrintLabels` →
`appendRepots` → `getPlants`). Each call took its own document lock. After a sale, cull, or repot,
predicted stock changed the plant-list fingerprint, so the follow-up `getPlants` was a full pull.
ADR-0008 rejected a *single atomic multi-tab* export (rollback of a successful queue when a sibling
fails). Independent queues inside one HTTP call are not that.

## Decision

One new Apps Script action `plantListSync`. Device-bound: access code + device prefix + device
secret, same Users-tab claim as `getPlants` / `append*` (ADR-0017).

The request omits empty queues and does not send `header`. Rows are positional against the four
export-header constants in `shared.js`, copied in lockstep with `core/` (`Export.HEADER`,
`CullExport.HEADER`, `LabelPrintExport.HEADER`, `RepotExport.HEADER`). Optional
`plantListFingerprint` is omitted on manual ↻ or an empty local cache (ADR-0016).

One document lock. Process sales → culls → labels → repots; catch per queue; do not roll back a
queue that already succeeded. Predicted stock still runs after successful sales/culls/repots,
**before** the fingerprint check. `unchanged` if the fingerprint still matches after that work (a
label-print-only export can match; empty body is not required). Else return the full plant list.
Import still runs when a queue fails (ADR-0007).

`ok: false` only before any queue write (auth, lock, bad JSON). After a queue is processed,
`ok: true` with nested `{ appended, skipped }` or `{ error }`. Mark local status EXPORTED only for a
queue object with `appended`/`skipped` and no `error`. Sheet SyncStatus: one stamp per processed
queue; plant-list stamp only when plants are returned.

Policy for request shaping and response interpretation lives in `core/` (`PlantListSync`). Android
I/O is an adapter. The new app calls **only** `plantListSync` (90s read timeout). Keep `getPlants`
and `append*` until a later Play release. Reverse sync is unchanged.

## Consequences

Ticker and manual ↻ share one HTTPS round trip. Partial queue success is the native Sheets model;
retry is safe because each tab still dedupes. Deploy the web app before the Play build that drops
the five-call path.
