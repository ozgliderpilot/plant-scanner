package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepotReadyForSaleTest {

    @Test fun `pots unchecked when pots is zero`() {
        val flags = RepotReadyForSale.defaults(
            tubes = 0, pots = 0, misc = 0,
            group = "Shrub", genus = "Acacia",
            sheetPotsForSale = true,
        )
        assertFalse(flags.pots)
    }

    @Test fun `pots checked when pots positive and not an exception plant`() {
        val flags = RepotReadyForSale.defaults(
            tubes = 0, pots = 5, misc = 0,
            group = "Shrub", genus = "Acacia",
            sheetPotsForSale = false,
        )
        assertTrue(flags.pots)
    }

    @Test fun `pots exception Camellia uses sheet potsForSale`() {
        assertTrue(
            RepotReadyForSale.defaults(
                tubes = 0, pots = 3, misc = 0,
                group = "Camellia", genus = "Camellia",
                sheetPotsForSale = true,
            ).pots,
        )
        assertFalse(
            RepotReadyForSale.defaults(
                tubes = 0, pots = 3, misc = 0,
                group = "camellia", genus = "Other",
                sheetPotsForSale = false,
            ).pots,
        )
    }

    @Test fun `pots exception Rhododendron uses sheet potsForSale`() {
        assertEquals(
            true,
            RepotReadyForSale.defaults(
                tubes = 0, pots = 2, misc = 0,
                group = "Rhododendron", genus = "Rhododendron",
                sheetPotsForSale = true,
            ).pots,
        )
        assertEquals(
            false,
            RepotReadyForSale.defaults(
                tubes = 0, pots = 2, misc = 0,
                group = "rhododendron", genus = "Other",
                sheetPotsForSale = false,
            ).pots,
        )
    }

    @Test fun `pots exception Hosta matches genus not Plant Type`() {
        assertFalse(
            RepotReadyForSale.defaults(
                tubes = 0, pots = 4, misc = 0,
                group = "Perennial", genus = "Hosta",
                sheetPotsForSale = false,
            ).pots,
        )
        assertTrue(
            RepotReadyForSale.defaults(
                tubes = 0, pots = 4, misc = 0,
                group = "Hosta", genus = "SomethingElse",
                sheetPotsForSale = false,
            ).pots,
        )
        assertTrue(
            RepotReadyForSale.defaults(
                tubes = 0, pots = 4, misc = 0,
                group = "Perennial", genus = "hosta",
                sheetPotsForSale = true,
            ).pots,
        )
    }

    @Test fun `tubes checked only when tubes positive and Plant Type is Herb`() {
        assertTrue(
            RepotReadyForSale.defaults(
                tubes = 8, pots = 0, misc = 0,
                group = "Herb", genus = "Ocimum",
                sheetPotsForSale = false,
            ).tubes,
        )
        assertTrue(
            RepotReadyForSale.defaults(
                tubes = 8, pots = 0, misc = 0,
                group = "herb", genus = "Ocimum",
                sheetPotsForSale = false,
            ).tubes,
        )
        assertFalse(
            RepotReadyForSale.defaults(
                tubes = 8, pots = 0, misc = 0,
                group = "Herbs", genus = "Ocimum",
                sheetPotsForSale = false,
            ).tubes,
        )
        assertFalse(
            RepotReadyForSale.defaults(
                tubes = 8, pots = 0, misc = 0,
                group = "Shrub", genus = "Ocimum",
                sheetPotsForSale = false,
            ).tubes,
        )
        assertFalse(
            RepotReadyForSale.defaults(
                tubes = 0, pots = 0, misc = 0,
                group = "Herb", genus = "Ocimum",
                sheetPotsForSale = false,
            ).tubes,
        )
    }

    @Test fun `misc checked only when misc positive`() {
        assertTrue(
            RepotReadyForSale.defaults(
                tubes = 0, pots = 0, misc = 2,
                group = "Shrub", genus = "Acacia",
                sheetPotsForSale = false,
            ).misc,
        )
        assertFalse(
            RepotReadyForSale.defaults(
                tubes = 0, pots = 0, misc = 0,
                group = "Shrub", genus = "Acacia",
                sheetPotsForSale = false,
            ).misc,
        )
    }
}
