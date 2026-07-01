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

    @Test fun `totalPendingCount combines SAVED receipts and PENDING culls`() {
        val culls = listOf(
            cull(1, CullStatus.PENDING),
            cull(2, CullStatus.PENDING),
            cull(3, CullStatus.EXPORTED),
        )
        assertEquals(4, Sync.totalPendingCount(receipts, culls)) // 2 SAVED + 2 PENDING
    }

    private fun cull(id: Long, status: CullStatus) = CullRecord(
        localId = id, cullNo = "PP-$id", createdAtEpochMs = 0, status = status,
        accession = "A", name = "X", group = null, isUnknown = false,
        qty = 1, unit = SaleUnit.TUBES, reason = CullReason.DEAD, notes = null,
    )
}
