package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals

class RepotSyncTest {

    private fun repot(status: RepotStatus) = RepotRecord(
        localId = 1,
        repotNo = "PP-1",
        createdAtEpochMs = 0,
        status = status,
        accession = "31011",
        name = "Acacia",
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

    @Test fun `pending returns only PENDING repots`() {
        val repots = listOf(repot(RepotStatus.PENDING), repot(RepotStatus.EXPORTED))
        assertEquals(1, RepotSync.pending(repots).size)
        assertEquals(RepotStatus.PENDING, RepotSync.pending(repots).single().status)
        assertEquals(1, RepotSync.pendingCount(repots))
    }

    @Test fun `markExported flips status`() {
        val exported = RepotSync.markExported(repot(RepotStatus.PENDING))
        assertEquals(RepotStatus.EXPORTED, exported.status)
    }
}
