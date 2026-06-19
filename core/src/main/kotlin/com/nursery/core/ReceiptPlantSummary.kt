package com.nursery.core

/**
 * The plant summary shown on a receipt **card** in the Receipts list, so volunteers can tell what
 * was sold without opening the receipt detail screen.
 *
 * [lines] are the first [MAX_NAMES] distinct plants (in first-seen order), each with the quantity
 * sold per sale-unit (Pots/Tubes/Misc) summed across that plant's line items. [remaining] is how
 * many further distinct plants are not shown, for an `…and X more` overflow line.
 *
 * Names are de-duplicated best-effort: a plant sold on several lines is listed once and its
 * quantities are summed per unit. A not-found "sell as unknown" line keeps its `name = "unknown"`
 * and is rendered as-is. An empty receipt yields empty [lines] and `remaining = 0`.
 */
data class ReceiptPlantSummary(val lines: List<Line>, val remaining: Int) {

    /** One de-duplicated plant on the card: its [name] and the quantity sold per sale-unit. */
    data class Line(val name: String, val pots: Int, val tubes: Int, val misc: Int) {
        /**
         * The non-zero unit counts as `"P 3 · T 1"` in fixed **P · T · M** order; `""` when every
         * count is zero (so the caller can show the name alone).
         */
        val counts: String
            get() = buildList {
                if (pots > 0) add("P $pots")
                if (tubes > 0) add("T $tubes")
                if (misc > 0) add("M $misc")
            }.joinToString(" · ")
    }

    companion object {
        /** How many distinct plants a card shows before collapsing the rest into `…and X more`. */
        const val MAX_NAMES = 3

        fun of(receipt: Receipt, max: Int = MAX_NAMES): ReceiptPlantSummary = of(receipt.lines, max)

        fun of(lineItems: List<LineItem>, max: Int = MAX_NAMES): ReceiptPlantSummary {
            // Sum qty per unit, grouped by name, preserving first-seen order. IntArray = [pots, tubes, misc].
            val byName = LinkedHashMap<String, IntArray>()
            for (li in lineItems) {
                val counts = byName.getOrPut(li.name) { IntArray(3) }
                when (li.unit) {
                    SaleUnit.POTS -> counts[0] += li.qty
                    SaleUnit.TUBES -> counts[1] += li.qty
                    SaleUnit.MISC -> counts[2] += li.qty
                }
            }
            val all = byName.map { (name, c) -> Line(name, pots = c[0], tubes = c[1], misc = c[2]) }
            val shown = all.take(max)
            return ReceiptPlantSummary(lines = shown, remaining = all.size - shown.size)
        }
    }
}
