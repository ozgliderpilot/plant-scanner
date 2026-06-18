package com.nursery.core

/**
 * Formats the per-accession stock counts for the Plant List screen.
 *
 * The Plants sheet (sourced from Access) carries four per-accession counts; this renders the
 * non-zero ones as a single line in the fixed order **Tubes, Pots, Misc., Stock**, each as
 * `"<Abbrev> <qty>"` joined by `" · "`:
 *
 *   T = Tubes, P = Pots, M = Misc., St = Stock plant.
 *
 * Example: 12 tubes, 5 pots, 0 misc, 3 stock → `"T 12 · P 5 · St 3"`. When every count is zero
 * (or negative) the result is `""` so the caller can omit the line entirely. A plant is a "stock
 * plant" when [Plant.stockInNursery] > 0.
 */
object PlantStock {

    fun summary(plant: Plant): String =
        buildList {
            if (plant.tubesInNursery > 0) add("T ${plant.tubesInNursery}")
            if (plant.potsInNursery > 0) add("P ${plant.potsInNursery}")
            if (plant.miscInNursery > 0) add("M ${plant.miscInNursery}")
            if (plant.stockInNursery > 0) add("St ${plant.stockInNursery}")
        }.joinToString(" · ")
}
