package com.nursery.core

/**
 * Nursery stock totals used to cap label reprint copies.
 *
 * **Nursery stock total** = pots + tubes + misc + stock counts on the accession (Access fields
 * `PotsInNursery`, `TubesInNursery`, `MiscInNursery`, `StockInNursery`). Negative sheet values
 * are treated as zero.
 */
object NurseryStock {

    fun total(plant: Plant): Int =
        nonNeg(plant.potsInNursery) +
            nonNeg(plant.tubesInNursery) +
            nonNeg(plant.miscInNursery) +
            nonNeg(plant.stockInNursery)

    /**
     * Null when [copies] is allowed for [stockTotal]; otherwise a volunteer-facing block message.
     * Callers should also enforce [LabelPrintRequest.validationError] separately (copies ≥ 1).
     */
    fun copiesCapError(copies: Int, stockTotal: Int): String? {
        if (copies <= stockTotal) return null
        val units = if (stockTotal == 1) "1 unit" else "$stockTotal units"
        val asked = if (copies == 1) "1 copy was" else "$copies copies were"
        return "The system only has $units in nursery stock, but $asked requested. " +
            "Ask the database administrator to update counts in Access before retrying."
    }

    private fun nonNeg(n: Int): Int = if (n < 0) 0 else n
}
