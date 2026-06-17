package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SaleUnitTest {

    @Test fun `pots in stock always wins`() {
        assertEquals(SaleUnit.POTS, SaleUnit.defaultFor(pots = 5, tubes = 5, misc = 5))
        assertEquals(SaleUnit.POTS, SaleUnit.defaultFor(pots = 1, tubes = 0, misc = 0))
    }

    @Test fun `no pots falls back to misc before tubes`() {
        assertEquals(SaleUnit.MISC, SaleUnit.defaultFor(pots = 0, tubes = 5, misc = 3))
        assertEquals(SaleUnit.MISC, SaleUnit.defaultFor(pots = 0, tubes = 0, misc = 2))
    }

    @Test fun `no pots or misc falls back to tubes`() {
        assertEquals(SaleUnit.TUBES, SaleUnit.defaultFor(pots = 0, tubes = 4, misc = 0))
    }

    @Test fun `all zero falls back to pots`() {
        assertEquals(SaleUnit.POTS, SaleUnit.defaultFor(pots = 0, tubes = 0, misc = 0))
    }

    @Test fun `labels are the sheet and dropdown strings`() {
        assertEquals("Pots", SaleUnit.POTS.label)
        assertEquals("Tubes", SaleUnit.TUBES.label)
        assertEquals("Misc", SaleUnit.MISC.label)
    }
}
