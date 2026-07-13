package com.nursery.core

/**
 * Repot sync selection + status transitions. Mirrors [CullSync]: only PENDING repots are
 * exported; once EXPORTED they are excluded from every later push.
 */
object RepotSync {

    fun pending(repots: List<RepotRecord>): List<RepotRecord> =
        repots.filter { it.status == RepotStatus.PENDING }

    fun pendingCount(repots: List<RepotRecord>): Int =
        repots.count { it.status == RepotStatus.PENDING }

    fun markExported(record: RepotRecord): RepotRecord = record.copy(status = RepotStatus.EXPORTED)
}
