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

    @Test fun `negative counts are treated as not in stock`() {
        assertEquals(SaleUnit.POTS, SaleUnit.defaultFor(pots = -1, tubes = 0, misc = 0))
        assertEquals(SaleUnit.TUBES, SaleUnit.defaultFor(pots = -5, tubes = 3, misc = 0))
    }

    @Test fun `labels are the sheet and dropdown strings`() {
        assertEquals("pots", SaleUnit.POTS.label)
        assertEquals("tubes", SaleUnit.TUBES.label)
        assertEquals("misc", SaleUnit.MISC.label)
    }

    @Test fun `labelFor is singular at one and plural otherwise`() {
        assertEquals("pot", SaleUnit.POTS.labelFor(1))
        assertEquals("pots", SaleUnit.POTS.labelFor(0))
        assertEquals("pots", SaleUnit.POTS.labelFor(2))
        assertEquals("tube", SaleUnit.TUBES.labelFor(1))
        assertEquals("tubes", SaleUnit.TUBES.labelFor(3))
        // misc is invariable
        assertEquals("misc", SaleUnit.MISC.labelFor(1))
        assertEquals("misc", SaleUnit.MISC.labelFor(5))
    }
}
