package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReceiptListTest {

    private fun receipt(id: Long, status: ReceiptStatus, createdAt: Long) =
        Receipt(localId = id, receiptNo = "PP-$id", createdAtEpochMs = createdAt, status = status, lines = emptyList())

    @Test fun `SAVED and OPEN are pending, EXPORTED is not`() {
        assertTrue(ReceiptList.isPending(ReceiptStatus.SAVED))
        assertTrue(ReceiptList.isPending(ReceiptStatus.OPEN))
        assertFalse(ReceiptList.isPending(ReceiptStatus.EXPORTED))
    }

    @Test fun `pending receipts are grouped above exported ones`() {
        val exported = receipt(1, ReceiptStatus.EXPORTED, createdAt = 100)
        val pending = receipt(2, ReceiptStatus.SAVED, createdAt = 50)
        // Input has the exported one first; grouping must move pending above it.
        val ordered = ReceiptList.grouped(listOf(exported, pending))
        assertEquals(listOf(2L, 1L), ordered.map { it.localId })
    }

    @Test fun `within each group, newest first`() {
        val pOld = receipt(1, ReceiptStatus.SAVED, createdAt = 10)
        val pNew = receipt(2, ReceiptStatus.SAVED, createdAt = 20)
        val eOld = receipt(3, ReceiptStatus.EXPORTED, createdAt = 5)
        val eNew = receipt(4, ReceiptStatus.EXPORTED, createdAt = 8)
        val ordered = ReceiptList.grouped(listOf(eOld, pOld, eNew, pNew))
        assertEquals(listOf(2L, 1L, 4L, 3L), ordered.map { it.localId })
    }

    @Test fun `all exported keeps date-desc order`() {
        val a = receipt(1, ReceiptStatus.EXPORTED, createdAt = 30)
        val b = receipt(2, ReceiptStatus.EXPORTED, createdAt = 10)
        val c = receipt(3, ReceiptStatus.EXPORTED, createdAt = 20)
        assertEquals(listOf(1L, 3L, 2L), ReceiptList.grouped(listOf(a, b, c)).map { it.localId })
    }

    @Test fun `all pending keeps date-desc order`() {
        val a = receipt(1, ReceiptStatus.SAVED, createdAt = 10)
        val b = receipt(2, ReceiptStatus.SAVED, createdAt = 30)
        assertEquals(listOf(2L, 1L), ReceiptList.grouped(listOf(a, b)).map { it.localId })
    }

    @Test fun `empty list yields empty`() {
        assertEquals(emptyList(), ReceiptList.grouped(emptyList()))
    }
}
