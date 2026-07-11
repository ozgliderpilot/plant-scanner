# Backend `shared.js`, sheet `sync_status`, reverse sync

Error-prone Apps Script logic lives in Node-testable `shared.js` (mirrored into `shared.gs`). Appended sales/culls get a sheet-only `sync_status` of Pending; Access reverse sync flips rows to Synced via mark APIs keyed by `(receipt, item_seq)` or `cull_id`. Dedupe on append uses those same identity keys so retries cannot double-count.
