package com.nursery.core

/**
 * Cull sync selection + status transitions. Mirrors [Sync] for receipts: only PENDING culls are
 * exported; once EXPORTED they are excluded from every later push.
 */
object CullSync {

    fun pending(culls: List<CullRecord>): List<CullRecord> =
        culls.filter { it.status == CullStatus.PENDING }

    fun pendingCount(culls: List<CullRecord>): Int =
        culls.count { it.status == CullStatus.PENDING }

    fun markExported(record: CullRecord): CullRecord = record.copy(status = CullStatus.EXPORTED)
}
