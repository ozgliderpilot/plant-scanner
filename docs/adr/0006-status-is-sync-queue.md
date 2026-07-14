# Local `status` is the sync queue

## Context

Sales, culls, label print requests, and repots must survive offline sessions and flaky uploads. A separate outbox table would duplicate state already needed on each record (“is this ready to push?”).

## Decision

Use the local `status` column as the sync queue: export only pending rows, and flip to exported **only after HTTP success**. Receipts: `OPEN` → `SAVED` → `EXPORTED`. Culls, label print requests, and repots: `PENDING` → `EXPORTED`.

## Consequences

“Nothing lost / no double-counting” is a plain transactional update. Rows stuck pending are visible and retryable; success must never be assumed before HTTP OK.
