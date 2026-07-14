package com.nursery.core

/**
 * Display ordering and status grouping for the View Repots list.
 *
 * Pending (not-yet-exported) repots sit above exported ones; newest-first within each group.
 */
object RepotList {

    fun isPending(status: RepotStatus): Boolean = status != RepotStatus.EXPORTED

    fun grouped(repots: List<RepotRecord>): List<RepotRecord> =
        repots.sortedWith(
            compareByDescending<RepotRecord> { isPending(it.status) }
                .thenByDescending { it.createdAtEpochMs },
        )
}
