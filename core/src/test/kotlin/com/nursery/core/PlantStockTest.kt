package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PlantStockTest {

    private fun plant(pots: Int = 0, tubes: Int = 0, misc: Int = 0, stock: Int = 0) =
        Plant(
            accession = "2021-0001", name = "Banksia", group = null, light = null,
            potsInNursery = pots, tubesInNursery = tubes, miscInNursery = misc, stockInNursery = stock,
        )

    @Test fun `all counts zero yields an empty string`() {
        assertEquals("", PlantStock.summary(plant()))
    }

    @Test fun `lists non-zero counts in fixed order Tubes Pots Misc Stock`() {
        assertEquals("T 12 · P 5 · St 3", PlantStock.summary(plant(tubes = 12, pots = 5, stock = 3)))
    }

    @Test fun `includes all four when all are non-zero`() {
        assertEquals("T 1 · P 2 · M 3 · St 4", PlantStock.summary(plant(tubes = 1, pots = 2, misc = 3, stock = 4)))
    }

    @Test fun `omits zero counts entirely`() {
        assertEquals("P 5", PlantStock.summary(plant(pots = 5)))
        assertEquals("M 7", PlantStock.summary(plant(misc = 7)))
        assertEquals("St 2", PlantStock.summary(plant(stock = 2)))
    }

    @Test fun `order is fixed regardless of which counts are present`() {
        assertEquals("T 4 · M 9", PlantStock.summary(plant(tubes = 4, misc = 9)))
        assertEquals("P 3 · St 1", PlantStock.summary(plant(pots = 3, stock = 1)))
    }

    @Test fun `negative counts are treated as absent`() {
        // Defensive: only strictly-positive counts are shown.
        assertEquals("P 2", PlantStock.summary(plant(pots = 2, tubes = -1)))
    }
}
