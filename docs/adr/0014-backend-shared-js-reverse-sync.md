# Backend `shared.js`, sheet `sync_status`, reverse sync

## Context

Apps Script runs in a hard-to-test host. Access must pull new Sheet rows into the nursery DB without double-applying retries. The device already tracks export with local `status`; Access needs its own Pending→Synced marker on the Sheet.

## Decision

Keep error-prone script logic in Node-testable `shared.js` (mirrored into `shared.gs`). Stamp
appended sales/culls/print labels/repots with sheet-only `sync_status` Pending; Access reverse sync
marks Synced (or NoMatch) via APIs keyed by `(receipt, item_seq)`, `cull_id`, `queue_id`, or
`repot_id`. Dedupe on append uses those same identity keys.

Access local ledgers (`tblAppliedSales`, `tblAppliedCulls`, `tblAppliedPrintLabels`,
`tblAppliedRepots`) are the authority against double-apply. **Synced** (and cull **StockPlant**)
rows are terminal on Pending pulls: Access only re-flips the Sheet. **NoMatch** is clearable: an
operator manually resets Sheet `sync_status` from NoMatch back to Pending; the next Access run
deletes that NoMatch ledger row and re-runs the normal apply path (so a batch that appeared later
can still sync). Synced ledger rows are never deleted.

## Consequences

Device cloud sync and Access reverse sync are separate legs. Column `sync_status` is sheet-owned — not part of the app export payload.
