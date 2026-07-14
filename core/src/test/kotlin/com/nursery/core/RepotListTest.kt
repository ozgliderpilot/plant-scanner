package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepotListTest {

    private fun repot(id: Long, status: RepotStatus, createdAt: Long) = RepotRecord(
        localId = id,
        repotNo = "PP-$id",
        createdAtEpochMs = createdAt,
        status = status,
        accession = "A$id",
        name = "Plant",
        group = null,
        tubesBefore = 1,
        potsBefore = 0,
        miscBefore = 0,
        stockBefore = 0,
        tubes = 0,
        pots = 1,
        misc = 0,
        stock = 0,
        tubesForSale = false,
        potsForSale = true,
        miscForSale = false,
    )

    @Test fun `PENDING is pending, EXPORTED is not`() {
        assertTrue(RepotList.isPending(RepotStatus.PENDING))
        assertFalse(RepotList.isPending(RepotStatus.EXPORTED))
    }

    @Test fun `pending repots are grouped above exported ones`() {
        val exported = repot(1, RepotStatus.EXPORTED, createdAt = 100)
        val pending = repot(2, RepotStatus.PENDING, createdAt = 50)
        assertEquals(listOf(2L, 1L), RepotList.grouped(listOf(exported, pending)).map { it.localId })
    }

    @Test fun `within each group, newest first`() {
        val pOld = repot(1, RepotStatus.PENDING, createdAt = 10)
        val pNew = repot(2, RepotStatus.PENDING, createdAt = 20)
        val eOld = repot(3, RepotStatus.EXPORTED, createdAt = 5)
        val eNew = repot(4, RepotStatus.EXPORTED, createdAt = 8)
        assertEquals(
            listOf(2L, 1L, 4L, 3L),
            RepotList.grouped(listOf(eOld, pOld, eNew, pNew)).map { it.localId },
        )
    }

    @Test fun `empty list yields empty`() {
        assertEquals(emptyList(), RepotList.grouped(emptyList()))
    }
}
