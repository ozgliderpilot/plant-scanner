package com.nursery.core

/**
 * The kind of unit a plant accession is sold in. [label] is shown in the line-item dropdown and is
 * the exact string written to the Sales sheet's "unit" column.
 */
enum class SaleUnit(val label: String) {
    POTS("Pots"),
    TUBES("Tubes"),
    MISC("Misc");

    companion object {
        /**
         * The unit pre-selected for a freshly scanned plant, from its in-nursery stock counts:
         * Pots, unless none in pots; then Misc, unless none; then Tubes, unless none; else Pots.
         * A not-found ("unknown") scan has all-zero counts and therefore defaults to Pots.
         */
        fun defaultFor(pots: Int, tubes: Int, misc: Int): SaleUnit = when {
            pots > 0 -> POTS
            misc > 0 -> MISC
            tubes > 0 -> TUBES
            else -> POTS
        }
    }
}
