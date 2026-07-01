package com.nursery.core

/**
 * Display ordering and status grouping for the culled-plants list.
 *
 * Pending (not-yet-exported) culls sit above exported ones; newest-first within each group.
 */
object CullList {

    fun isPending(status: CullStatus): Boolean = status != CullStatus.EXPORTED

    fun grouped(culls: List<CullRecord>): List<CullRecord> =
        culls.sortedWith(
            compareByDescending<CullRecord> { isPending(it.status) }
                .thenByDescending { it.createdAtEpochMs },
        )
}
