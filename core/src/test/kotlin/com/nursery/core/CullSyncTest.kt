package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CullSyncTest {

    private fun cull(status: CullStatus) = CullRecord(
        localId = 1,
        cullNo = "PP-1",
        createdAtEpochMs = 0,
        status = status,
        accession = "31011",
        name = "Acacia",
        group = null,
        isUnknown = false,
        qty = 1,
        unit = SaleUnit.TUBES,
        reason = CullReason.DEAD,
        notes = null,
    )

    @Test fun `pending returns only PENDING culls`() {
        val culls = listOf(cull(CullStatus.PENDING), cull(CullStatus.EXPORTED))
        assertEquals(1, CullSync.pending(culls).size)
        assertEquals(CullStatus.PENDING, CullSync.pending(culls).single().status)
    }

    @Test fun `markExported flips status`() {
        val exported = CullSync.markExported(cull(CullStatus.PENDING))
        assertEquals(CullStatus.EXPORTED, exported.status)
    }
}
