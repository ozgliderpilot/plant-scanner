# Unified cloud sync: export then import

## Context

Volunteers need pending sales/culls/labels/repots pushed and the plant list refreshed without juggling two manual actions. WorkManager’s periodic floor is 15 minutes — too slow for a selling session — and volunteers keep the app open while working.

## Decision

One entry point (`SyncRepository.syncCloud`) always exports the sync queue (sales, then culls, then label print requests, then repots) and then imports the plant list — import still runs when export fails or the queue is empty. History ↻, Plants ↻, and the in-app coroutine ticker (default 60s, configurable) share that path. Not WorkManager.

## Consequences

Every refresh path uses the same mutex-serialized sequence. Background sync while the app is closed is out of scope; promote to a foreground Service only if that need appears.
