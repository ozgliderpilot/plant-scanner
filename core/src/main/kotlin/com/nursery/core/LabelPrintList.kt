package com.nursery.core

/**
 * Display ordering and status grouping for the View Labels list.
 *
 * Pending (not-yet-exported) requests sit above exported ones; newest-first within each group.
 */
object LabelPrintList {

    fun isPending(status: LabelPrintStatus): Boolean = status != LabelPrintStatus.EXPORTED

    fun grouped(requests: List<LabelPrintRequest>): List<LabelPrintRequest> =
        requests.sortedWith(
            compareByDescending<LabelPrintRequest> { isPending(it.status) }
                .thenByDescending { it.createdAtEpochMs },
        )
}
