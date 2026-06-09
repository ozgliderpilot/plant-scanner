package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncTest {

    private fun receipt(id: Long, status: ReceiptStatus) =
        Receipt(localId = id, receiptNo = "07-$id", createdAtEpochMs = 0, status = status, lines = emptyList())

    private val receipts = listOf(
        receipt(1, ReceiptStatus.OPEN),
        receipt(2, ReceiptStatus.SAVED),
        receipt(3, ReceiptStatus.SAVED),
        receipt(4, ReceiptStatus.EXPORTED),
    )

    @Test fun `pending is only SAVED`() {
        assertEquals(listOf(2L, 3L), Sync.pending(receipts).map { it.localId })
        assertEquals(2, Sync.pendingCount(receipts))
    }

    @Test fun `exported receipts are never pending again`() {
        val exported = Sync.markExported(receipts[1]) // was SAVED #2
        val after = receipts.map { if (it.localId == 2L) exported else it }
        assertFalse(Sync.pending(after).any { it.localId == 2L })
        assertEquals(1, Sync.pendingCount(after))
    }

    @Test fun `markSaved flips OPEN to SAVED`() {
        val saved = Sync.markSaved(receipts[0])
        assertEquals(ReceiptStatus.SAVED, saved.status)
        assertTrue(Sync.pending(listOf(saved)).isNotEmpty())
    }
}
