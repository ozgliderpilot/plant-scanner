package com.nursery.core

/**
 * Display ordering and status grouping for the Receipts list.
 *
 * The list is split by a "waterline": receipts still pending export sit above (newest first),
 * already-exported receipts below. This replaces the per-card status label — a pending card is
 * shown with an accent instead. A receipt is "pending" until it reaches EXPORTED, so a freshly
 * SAVED receipt (and, defensively, a still-OPEN one) counts as pending.
 */
object ReceiptList {

    /** True while a receipt still needs exporting — i.e. anything that has not reached EXPORTED. */
    fun isPending(status: ReceiptStatus): Boolean = status != ReceiptStatus.EXPORTED

    /** Pending receipts first, then exported; newest-first (createdAt desc) within each group. */
    fun grouped(receipts: List<Receipt>): List<Receipt> =
        receipts.sortedWith(
            compareByDescending<Receipt> { isPending(it.status) }
                .thenByDescending { it.createdAtEpochMs },
        )
}
