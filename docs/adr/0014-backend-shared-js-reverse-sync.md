# Backend `shared.js`, sheet `sync_status`, reverse sync

## Context

Apps Script runs in a hard-to-test host. Access must pull new Sheet rows into the nursery DB without double-applying retries. The device already tracks export with local `status`; Access needs its own Pending→Synced marker on the Sheet.

## Decision

Keep error-prone script logic in Node-testable `shared.js` (mirrored into `shared.gs`). Stamp appended sales/culls with sheet-only `sync_status` Pending; Access reverse sync marks Synced via APIs keyed by `(receipt, item_seq)` or `cull_id`. Dedupe on append uses those same identity keys.

## Consequences

Device cloud sync and Access reverse sync are separate legs. Column `sync_status` is sheet-owned — not part of the app export payload.
