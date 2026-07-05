package com.nursery.core

import java.time.Instant
import java.time.ZoneId

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

    /**
     * Interleaves [grouped] receipts with a day-total row after the last receipt for each calendar day.
     * Totals include every receipt on that day, even when pending/export grouping splits them apart.
     * [receipts] must already be in display order (typically from [grouped]).
     */
    fun withDayTotals(receipts: List<Receipt>, zone: ZoneId): List<ReceiptListItem> {
        if (receipts.isEmpty()) return emptyList()
        val totalsByDay = receipts
            .groupBy { epochDay(it.createdAtEpochMs, zone) }
            .mapValues { (_, dayReceipts) -> dayReceipts.sumOf { Money.receiptTotalCents(it.lines) } }
        val lastIndexByDay = receipts
            .mapIndexed { index, receipt -> epochDay(receipt.createdAtEpochMs, zone) to index }
            .toMap()
        return buildList {
            receipts.forEachIndexed { index, receipt ->
                add(ReceiptListItem.Row(receipt))
                val day = epochDay(receipt.createdAtEpochMs, zone)
                if (lastIndexByDay.getValue(day) == index) {
                    add(ReceiptListItem.DayTotal(epochDay = day, totalCents = totalsByDay.getValue(day)))
                }
            }
        }
    }

    private fun epochDay(createdAtEpochMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(createdAtEpochMs).atZone(zone).toLocalDate().toEpochDay()
}

/** One row in the Receipts list — either a receipt card or a day-total footer. */
sealed interface ReceiptListItem {
    data class Row(val receipt: Receipt) : ReceiptListItem
    data class DayTotal(val epochDay: Long, val totalCents: Long) : ReceiptListItem
}
