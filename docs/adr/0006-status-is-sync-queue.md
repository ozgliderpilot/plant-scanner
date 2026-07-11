# Local `status` is the sync queue

Receipts, culls, and label print requests use a local `status` column as the sync queue: export only pending rows, and flip to exported **only after HTTP success**. That delivers “nothing lost / no double-counting” with a plain transactional update — no separate outbox table.
