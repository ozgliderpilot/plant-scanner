# Snapshots at save; post-save immutable

## Context

Plant-list imports rewrite the local cache. If export re-looked up live plant rows, a later sync could change what a past sale “meant.” Editing saved receipts on-device would also fight the export/idempotency model.

## Decision

Snapshot taxonomic identity (and related display fields) onto line items and cull records at save time — export uses those frozen fields. After save, receipts and culls are immutable in the app (history is display-only).

## Consequences

Historical Sheet rows stay consistent with what the volunteer saw at save. Corrections after save happen outside the app (or via new records), not by mutating exported identity.
