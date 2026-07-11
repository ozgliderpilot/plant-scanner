# Unified cloud sync: export then import

One entry point (`SyncRepository.syncCloud`) always exports the sync queue (sales, then culls, then label print requests) and then imports the plant list — import still runs when export fails or the queue is empty. History ↻, Plants ↻, and the in-app coroutine ticker (default 60s, configurable) share that path; WorkManager is not used because its periodic floor is 15 minutes and volunteers keep the app open while selling.
