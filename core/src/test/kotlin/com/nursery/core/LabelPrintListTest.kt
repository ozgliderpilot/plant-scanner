package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LabelPrintListTest {

    private fun request(id: Long, status: LabelPrintStatus, createdAt: Long) = LabelPrintRequest(
        localId = id,
        queueId = "PP-$id",
        createdAtEpochMs = createdAt,
        status = status,
        accession = "A$id",
        name = "Plant",
        copies = 1,
    )

    @Test fun `PENDING is pending, EXPORTED is not`() {
        assertTrue(LabelPrintList.isPending(LabelPrintStatus.PENDING))
        assertFalse(LabelPrintList.isPending(LabelPrintStatus.EXPORTED))
    }

    @Test fun `pending requests are grouped above exported ones`() {
        val exported = request(1, LabelPrintStatus.EXPORTED, createdAt = 100)
        val pending = request(2, LabelPrintStatus.PENDING, createdAt = 50)
        assertEquals(
            listOf(2L, 1L),
            LabelPrintList.grouped(listOf(exported, pending)).map { it.localId },
        )
    }

    @Test fun `within each group, newest first`() {
        val pOld = request(1, LabelPrintStatus.PENDING, createdAt = 10)
        val pNew = request(2, LabelPrintStatus.PENDING, createdAt = 20)
        val eOld = request(3, LabelPrintStatus.EXPORTED, createdAt = 5)
        val eNew = request(4, LabelPrintStatus.EXPORTED, createdAt = 8)
        assertEquals(
            listOf(2L, 1L, 4L, 3L),
            LabelPrintList.grouped(listOf(eOld, pOld, eNew, pNew)).map { it.localId },
        )
    }

    @Test fun `empty list yields empty`() {
        assertEquals(emptyList(), LabelPrintList.grouped(emptyList()))
    }
}
