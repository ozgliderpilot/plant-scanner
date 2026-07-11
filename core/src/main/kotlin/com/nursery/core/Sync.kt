package com.nursery.core

/**
 * Sync selection + status transitions. The whole "no double-counting" guarantee (spec) lives
 * here: only SAVED receipts are pending; once EXPORTED they are excluded from every later push.
 */
object Sync {

    /** Receipts awaiting export = those saved locally but not yet exported. */
    fun pending(receipts: List<Receipt>): List<Receipt> =
        receipts.filter { it.status == ReceiptStatus.SAVED }

    fun pendingCount(receipts: List<Receipt>): Int =
        receipts.count { it.status == ReceiptStatus.SAVED }

    fun markSaved(receipt: Receipt): Receipt = receipt.copy(status = ReceiptStatus.SAVED)

    fun markExported(receipt: Receipt): Receipt = receipt.copy(status = ReceiptStatus.EXPORTED)

    /** Combined pending count for the status chip, success screens, and Data export tile. */
    fun totalPendingCount(
        receipts: List<Receipt>,
        culls: List<CullRecord>,
        labelPrints: List<LabelPrintRequest>,
    ): Int =
        pendingCount(receipts) +
            CullSync.pendingCount(culls) +
            LabelPrintSync.pendingCount(labelPrints)
}
