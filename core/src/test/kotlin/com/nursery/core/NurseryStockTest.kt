package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NurseryStockTest {

    private fun plant(
        pots: Int = 0,
        tubes: Int = 0,
        misc: Int = 0,
        stock: Int = 0,
    ) = Plant(
        accession = "31011",
        name = "Acacia",
        group = null,
        light = null,
        potsInNursery = pots,
        tubesInNursery = tubes,
        miscInNursery = misc,
        stockInNursery = stock,
    )

    @Test fun `total sums all four nursery counts`() {
        assertEquals(0, NurseryStock.total(plant()))
        assertEquals(21, NurseryStock.total(plant(pots = 5, tubes = 12, misc = 1, stock = 3)))
    }

    @Test fun `negative counts do not reduce the total below the positive sum`() {
        // Defensive: treat negatives as zero so a bad sheet row cannot inflate the cap oddly.
        assertEquals(5, NurseryStock.total(plant(pots = 5, tubes = -2)))
    }

    @Test fun `copiesAllowed when within stock total`() {
        assertNull(NurseryStock.copiesCapError(copies = 1, stockTotal = 5))
        assertNull(NurseryStock.copiesCapError(copies = 5, stockTotal = 5))
    }

    @Test fun `copies blocked when over stock total`() {
        assertEquals(
            "The system only has 3 units in nursery stock, but 5 copies were requested. " +
                "Ask the database administrator to update counts in Access before retrying.",
            NurseryStock.copiesCapError(copies = 5, stockTotal = 3),
        )
    }

    @Test fun `zero stock blocks any positive copies request`() {
        assertEquals(
            "The system only has 0 units in nursery stock, but 1 copy was requested. " +
                "Ask the database administrator to update counts in Access before retrying.",
            NurseryStock.copiesCapError(copies = 1, stockTotal = 0),
        )
    }
}
