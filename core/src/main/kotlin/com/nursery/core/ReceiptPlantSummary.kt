package com.nursery.core

/**
 * The plant-name summary shown on a receipt **card** in the Receipts list, so volunteers can tell
 * what was sold without opening the receipt detail screen.
 *
 * [names] are the first [MAX_NAMES] distinct plant names on the receipt (in first-seen order);
 * [remaining] is how many further distinct names are not shown, for an `…and X more` overflow line.
 * Names are de-duplicated best-effort (a plant sold on several lines is listed once; per-line qty
 * lives on the detail screen). A not-found "sell as unknown" line keeps its `name = "unknown"` and
 * is rendered as-is. An empty receipt yields empty [names] and `remaining = 0` (no overflow line).
 */
data class ReceiptPlantSummary(val names: List<String>, val remaining: Int) {
    companion object {
        /** How many plant names a card shows before collapsing the rest into `…and X more`. */
        const val MAX_NAMES = 3

        fun of(receipt: Receipt, max: Int = MAX_NAMES): ReceiptPlantSummary = of(receipt.lines, max)

        fun of(lines: List<LineItem>, max: Int = MAX_NAMES): ReceiptPlantSummary {
            val distinct = lines.map { it.name }.distinct()
            val shown = distinct.take(max)
            return ReceiptPlantSummary(names = shown, remaining = distinct.size - shown.size)
        }
    }
}
