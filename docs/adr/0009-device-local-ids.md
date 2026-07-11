# Device-local IDs: `PP-<epoch>-<seq>`

Sale receipts, culls, and label print requests share one per-device numbering scheme: a two-digit device prefix from settings, epoch seconds, and a daily-resetting sequence (`PP-<epochSeconds>-<seq>`). Prefixes avoid cross-device collisions in the shared Sheet; the epoch segment survives reinstalls after a sequence reset — no central ID service.
