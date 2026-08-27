package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepotReadyForSaleTest {

    private fun plant(
        tubes: Int = 0,
        pots: Int = 0,
        misc: Int = 0,
        group: String? = "Shrub",
        genus: String = "Acacia",
        tubesForSale: Boolean = false,
        potsForSale: Boolean = false,
        miscForSale: Boolean = false,
    ) = Plant(
        accession = "31011",
        name = "Acacia pycnantha",
        genus = genus,
        group = group,
        light = null,
        potsInNursery = pots,
        tubesInNursery = tubes,
        miscInNursery = misc,
        potsForSale = potsForSale,
        tubesForSale = tubesForSale,
        miscForSale = miscForSale,
    )

    @Test fun `pots tick follows the plant list even when pots count is positive`() {
        val flags = RepotReadyForSale.fromPlant(
            plant(pots = 5, potsForSale = false, group = "Shrub", genus = "Acacia"),
        )
        assertFalse(flags.pots)
    }

    @Test fun `misc tick follows the plant list even when misc count is positive`() {
        val flags = RepotReadyForSale.fromPlant(
            plant(misc = 2, miscForSale = false),
        )
        assertFalse(flags.misc)
    }

    @Test fun `tubes tick follows the plant list even for Herb with tubes`() {
        val flags = RepotReadyForSale.fromPlant(
            plant(tubes = 8, group = "Herb", genus = "Ocimum", tubesForSale = false),
        )
        assertFalse(flags.tubes)
    }

    @Test fun `checked plant-list ticks stay checked`() {
        val flags = RepotReadyForSale.fromPlant(
            plant(
                tubes = 3, pots = 4, misc = 1,
                tubesForSale = true, potsForSale = true, miscForSale = true,
            ),
        )
        assertEquals(ReadyForSaleFlags(tubes = true, pots = true, misc = true), flags)
    }

    @Test fun `former pots exceptions still follow the plant list`() {
        assertFalse(
            RepotReadyForSale.fromPlant(
                plant(pots = 3, group = "Camellia", genus = "Camellia", potsForSale = false),
            ).pots,
        )
        assertTrue(
            RepotReadyForSale.fromPlant(
                plant(pots = 2, group = "Rhododendron", genus = "Rhododendron", potsForSale = true),
            ).pots,
        )
        assertFalse(
            RepotReadyForSale.fromPlant(
                plant(pots = 4, group = "Perennial", genus = "Hosta", potsForSale = false),
            ).pots,
        )
    }

    @Test fun `zero counts still show the plant-list ticks`() {
        val flags = RepotReadyForSale.fromPlant(
            plant(
                tubes = 0, pots = 0, misc = 0,
                tubesForSale = true, potsForSale = true, miscForSale = true,
            ),
        )
        assertEquals(ReadyForSaleFlags(tubes = true, pots = true, misc = true), flags)
    }
}
