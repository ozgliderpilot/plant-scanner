package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CullUnitTest {

    @Test fun `tubes in stock always wins`() {
        assertEquals(SaleUnit.TUBES, CullUnit.defaultFor(tubes = 5, pots = 5, misc = 5))
        assertEquals(SaleUnit.TUBES, CullUnit.defaultFor(tubes = 1, pots = 0, misc = 0))
    }

    @Test fun `no tubes falls back to pots before misc`() {
        assertEquals(SaleUnit.POTS, CullUnit.defaultFor(tubes = 0, pots = 5, misc = 3))
        assertEquals(SaleUnit.POTS, CullUnit.defaultFor(tubes = 0, pots = 2, misc = 0))
    }

    @Test fun `no tubes or pots falls back to misc`() {
        assertEquals(SaleUnit.MISC, CullUnit.defaultFor(tubes = 0, pots = 0, misc = 4))
    }

    @Test fun `all zero falls back to tubes`() {
        assertEquals(SaleUnit.TUBES, CullUnit.defaultFor(tubes = 0, pots = 0, misc = 0))
    }

    @Test fun `negative counts are treated as not in stock`() {
        assertEquals(SaleUnit.TUBES, CullUnit.defaultFor(tubes = -1, pots = 0, misc = 0))
        assertEquals(SaleUnit.POTS, CullUnit.defaultFor(tubes = -5, pots = 3, misc = 0))
    }
}
