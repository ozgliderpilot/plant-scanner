# Unified cloud sync (export then import)

**Date:** 2026-07-10
**Status:** Approved

## Context

History ↻ only exported the sync queue; Plants ↻ only imported the plant list; the background
ticker only exported. Volunteers tapping either ↻ expected both lists to refresh. An empty sync
queue also short-circuited without touching the plant list.

## Decision

One **cloud sync** entry point (`SyncRepository.syncCloud`) always:

1. Exports pending sales, then culls, then label print requests (existing queue rules: flip to
   exported only on HTTP success).
2. Then imports the plant list, even if export failed or the queue was empty.

History ↻, Plants ↻, and the background ticker all call that entry point. Both steps are skipped
only when the device is not configured.

Timestamps:

- Export “Updated” advances when the export step finishes without error (including an empty queue).
- Plant-list “Updated” advances only on successful import.

Overall result fails if either step fails; when both fail, surface the export error (queue/money
first). Combine rules live in `core/CloudSync`.

## Consequences

- Manual and automatic sync share one code path under `cloudMutex`.
- Plant list stays fresher during selling sessions (ticker imports as well as exports).
- UI copy/toasts may still say “Synced” rather than separate export/import wording.
