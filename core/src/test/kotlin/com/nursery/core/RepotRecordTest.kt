package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepotRecordTest {

    private fun record(
        tubesBefore: Int = 5,
        potsBefore: Int = 0,
        miscBefore: Int = 0,
        stockBefore: Int = 0,
        tubes: Int = 0,
        pots: Int = 5,
        misc: Int = 0,
        stock: Int = 0,
        tubesForSale: Boolean = false,
        potsForSale: Boolean = true,
        miscForSale: Boolean = false,
    ) = RepotRecord(
        localId = 1,
        repotNo = "PP-1",
        createdAtEpochMs = 0,
        status = RepotStatus.PENDING,
        accession = "31011",
        name = "Acacia pycnantha",
        genus = "Acacia",
        species = "pycnantha",
        cultivar = "",
        commonName = "Golden Wattle",
        group = "Tree",
        tubesBefore = tubesBefore,
        potsBefore = potsBefore,
        miscBefore = miscBefore,
        stockBefore = stockBefore,
        tubes = tubes,
        pots = pots,
        misc = misc,
        stock = stock,
        tubesForSale = tubesForSale,
        potsForSale = potsForSale,
        miscForSale = miscForSale,
    )

    private val initialForSale = ReadyForSaleFlags(tubes = false, pots = true, misc = false)

    @Test fun `rejects negative counts`() {
        assertEquals(
            "Counts cannot be negative",
            RepotRecord.validationError(record(tubes = -1), initialForSale),
        )
        assertEquals(
            "Counts cannot be negative",
            RepotRecord.validationError(record(potsBefore = -1, pots = 0), initialForSale),
        )
        assertEquals(
            "Counts cannot be negative",
            RepotRecord.validationError(record(stock = -2), initialForSale),
        )
    }

    @Test fun `rejects no-op save when counts and ticks match initial`() {
        val unchanged = record(
            tubesBefore = 5, potsBefore = 0, miscBefore = 0, stockBefore = 0,
            tubes = 5, pots = 0, misc = 0, stock = 0,
            tubesForSale = false, potsForSale = true, miscForSale = false,
        )
        assertEquals(
            "Nothing changed",
            RepotRecord.validationError(unchanged, initialForSale),
        )
    }

    @Test fun `allows save when a count changed`() {
        assertNull(RepotRecord.validationError(record(), initialForSale))
    }

    @Test fun `allows save when only a for-sale tick changed`() {
        val tickOnly = record(
            tubesBefore = 5, potsBefore = 0, miscBefore = 0, stockBefore = 0,
            tubes = 5, pots = 0, misc = 0, stock = 0,
            tubesForSale = false, potsForSale = false, miscForSale = false,
        )
        assertNull(RepotRecord.validationError(tickOnly, initialForSale))
    }

    @Test fun `allows all-zero counts when that is a change`() {
        assertNull(
            RepotRecord.validationError(
                record(
                    tubesBefore = 1, potsBefore = 0, miscBefore = 0, stockBefore = 0,
                    tubes = 0, pots = 0, misc = 0, stock = 0,
                    tubesForSale = false, potsForSale = false, miscForSale = false,
                ),
                ReadyForSaleFlags(tubes = false, pots = false, misc = false),
            ),
        )
    }

    @Test fun `not-found message is exact administrator contact copy`() {
        assertEquals(
            "We can’t find this plant. Please ask the database administrator.",
            RepotRecord.NOT_FOUND_MESSAGE,
        )
    }

    @Test fun `isAllZeroCounts is true only when T P M and St are all zero`() {
        assertTrue(record(tubes = 0, pots = 0, misc = 0, stock = 0).isAllZeroCounts())
        assertFalse(record(tubes = 1, pots = 0, misc = 0, stock = 0).isAllZeroCounts())
        assertFalse(record(tubes = 0, pots = 1, misc = 0, stock = 0).isAllZeroCounts())
        assertFalse(record(tubes = 0, pots = 0, misc = 1, stock = 0).isAllZeroCounts())
        assertFalse(record(tubes = 0, pots = 0, misc = 0, stock = 1).isAllZeroCounts())
    }
}
