# Device-local IDs: `PP-<epoch>-<seq>`

## Context

Multiple devices write into one shared Sheet. There is no central ID service, and a reinstall must not reuse yesterday’s sequence in a way that collides with already-exported rows.

## Decision

Sale receipts, culls, and label print requests share one per-device scheme: two-digit device prefix from settings, epoch seconds, and a daily-resetting sequence (`PP-<epochSeconds>-<seq>`).

## Consequences

IDs are unique across devices without coordination. Operators must keep device prefixes distinct; the daily counter is shared across sales, culls, and labels on that device.
