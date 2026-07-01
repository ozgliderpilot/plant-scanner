package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CullListTest {

    private fun cull(id: Long, status: CullStatus, createdAt: Long) = CullRecord(
        localId = id,
        cullNo = "PP-$id",
        createdAtEpochMs = createdAt,
        status = status,
        accession = "A$id",
        name = "Plant",
        group = null,
        isUnknown = false,
        qty = 1,
        unit = SaleUnit.TUBES,
        reason = CullReason.DEAD,
        notes = null,
    )

    @Test fun `PENDING is pending, EXPORTED is not`() {
        assertTrue(CullList.isPending(CullStatus.PENDING))
        assertFalse(CullList.isPending(CullStatus.EXPORTED))
    }

    @Test fun `pending culls are grouped above exported ones`() {
        val exported = cull(1, CullStatus.EXPORTED, createdAt = 100)
        val pending = cull(2, CullStatus.PENDING, createdAt = 50)
        assertEquals(listOf(2L, 1L), CullList.grouped(listOf(exported, pending)).map { it.localId })
    }

    @Test fun `within each group, newest first`() {
        val pOld = cull(1, CullStatus.PENDING, createdAt = 10)
        val pNew = cull(2, CullStatus.PENDING, createdAt = 20)
        val eOld = cull(3, CullStatus.EXPORTED, createdAt = 5)
        val eNew = cull(4, CullStatus.EXPORTED, createdAt = 8)
        assertEquals(listOf(2L, 1L, 4L, 3L), CullList.grouped(listOf(eOld, pOld, eNew, pNew)).map { it.localId })
    }

    @Test fun `empty list yields empty`() {
        assertEquals(emptyList(), CullList.grouped(emptyList()))
    }
}
