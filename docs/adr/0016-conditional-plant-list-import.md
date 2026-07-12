# Conditional plant-list import via fingerprint

## Context

Every cloud sync downloaded and wholesale-replaced the plant list even when the Sheet-side list had not changed. That wasted bandwidth and churned the local Room cache on every ticker tick. Access already skips unchanged *pushes* via its own `LastHash`; the device import path had no equivalent. Import must still *run* on every sync (ADR-0007) — the need is a cheap successful no-op when the list is unchanged.

## Decision

Gate plant-list download and local replace on a **plant-list fingerprint**: an opaque string identifying the current list as returned by `getPlants` (parsed plant objects, not raw sheet cells, not Access push `LastHash`).

- Extend `getPlants` with optional request `plantListFingerprint` and response fields `unchanged` / `plantListFingerprint`. Matching fingerprints yield `{ unchanged: true }` with no plant rows and no SyncStatus stamp; otherwise a full payload plus fingerprint.
- Cache the current fingerprint in Apps Script Script Properties; recompute after `replacePlants`, predicted-stock Plant mutations, and any full `getPlants` response (self-heal).
- Device only echoes the fingerprint — never hashes locally. Persist only after a successful local replace. Manual ↻ and empty local cache omit the fingerprint (force full pull). Unchanged import still advances last plant-list update.
- Apply-vs-keep-cache policy lives in `core/` (`PlantListImport`); fingerprint hashing lives in backend `shared.js`.

## Consequences

Ticker syncs stay cheap when the catalogue is stable. Manual ↻ always re-downloads. Manual Sheet edits that bypass Access/`replacePlants` can leave the Script Properties cache stale until the next full pull or write path refreshes it — volunteers force-refresh when they need a guaranteed fresh list.
