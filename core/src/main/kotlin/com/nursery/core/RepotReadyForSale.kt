package com.nursery.core

/** Three Ready-for-sale ticks on a Repot (Tubes / Pots / Misc. — never Stock plant). */
data class ReadyForSaleFlags(
    val tubes: Boolean,
    val pots: Boolean,
    val misc: Boolean,
)

/**
 * Initial Ready-for-sale ticks when opening the Repot count editor: the plant list
 * (sheet) values, not count-based guesses. Toggling a tick is then a real change.
 */
object RepotReadyForSale {

    fun fromPlant(plant: Plant): ReadyForSaleFlags = ReadyForSaleFlags(
        tubes = plant.tubesForSale,
        pots = plant.potsForSale,
        misc = plant.miscForSale,
    )
}
