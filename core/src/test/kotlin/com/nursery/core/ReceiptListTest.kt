package com.nursery.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReceiptListTest {

    private val zone = ZoneId.of("UTC")

    private fun receipt(id: Long, status: ReceiptStatus, createdAt: Long, unitPriceCents: Long = 0) =
        Receipt(
            localId = id,
            receiptNo = "PP-$id",
            createdAtEpochMs = createdAt,
            status = status,
            lines = if (unitPriceCents == 0L) {
                emptyList()
            } else {
                listOf(LineItem("A1", "Test", qty = 1, unitPriceCents = unitPriceCents, discountPct = 0))
            },
        )

    private fun epochMs(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        LocalDate.of(year, month, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

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

    @Test fun `withDayTotals appends a total after each calendar day block`() {
        val july3 = epochMs(2026, 7, 3)
        val july4 = epochMs(2026, 7, 4)
        val a = receipt(1, ReceiptStatus.EXPORTED, july4, unitPriceCents = 5000)
        val b = receipt(2, ReceiptStatus.EXPORTED, july3, unitPriceCents = 7550)
        val items = ReceiptList.withDayTotals(ReceiptList.grouped(listOf(a, b)), zone)
        assertEquals(
            listOf(
                ReceiptListItem.Row(a),
                ReceiptListItem.DayTotal(epochDay = LocalDate.of(2026, 7, 4).toEpochDay(), totalCents = 5000),
                ReceiptListItem.Row(b),
                ReceiptListItem.DayTotal(epochDay = LocalDate.of(2026, 7, 3).toEpochDay(), totalCents = 7550),
            ),
            items,
        )
    }

    @Test fun `withDayTotals sums multiple receipts on the same day`() {
        val july3noon = epochMs(2026, 7, 3, hour = 12)
        val july3evening = epochMs(2026, 7, 3, hour = 18)
        val newer = receipt(1, ReceiptStatus.EXPORTED, july3evening, unitPriceCents = 2000)
        val older = receipt(2, ReceiptStatus.EXPORTED, july3noon, unitPriceCents = 1050)
        val items = ReceiptList.withDayTotals(ReceiptList.grouped(listOf(newer, older)), zone)
        assertEquals(
            listOf(
                ReceiptListItem.Row(newer),
                ReceiptListItem.Row(older),
                ReceiptListItem.DayTotal(epochDay = LocalDate.of(2026, 7, 3).toEpochDay(), totalCents = 3050),
            ),
            items,
        )
    }

    @Test fun `withDayTotals splits receipts across a local midnight boundary`() {
        val late = epochMs(2026, 7, 3, hour = 23)
        val early = epochMs(2026, 7, 4, hour = 1)
        val items = ReceiptList.withDayTotals(
            ReceiptList.grouped(
                listOf(
                    receipt(1, ReceiptStatus.EXPORTED, early, unitPriceCents = 100),
                    receipt(2, ReceiptStatus.EXPORTED, late, unitPriceCents = 200),
                ),
            ),
            zone,
        )
        assertEquals(
            listOf(
                ReceiptListItem.Row(receipt(1, ReceiptStatus.EXPORTED, early, unitPriceCents = 100)),
                ReceiptListItem.DayTotal(epochDay = LocalDate.of(2026, 7, 4).toEpochDay(), totalCents = 100),
                ReceiptListItem.Row(receipt(2, ReceiptStatus.EXPORTED, late, unitPriceCents = 200)),
                ReceiptListItem.DayTotal(epochDay = LocalDate.of(2026, 7, 3).toEpochDay(), totalCents = 200),
            ),
            items,
        )
    }

    @Test fun `withDayTotals on empty list yields empty`() {
        assertEquals(emptyList(), ReceiptList.withDayTotals(emptyList(), zone))
    }

    @Test fun `withDayTotals sums all receipts on a day split across pending and exported`() {
        val july3 = epochMs(2026, 7, 3)
        val pending = receipt(1, ReceiptStatus.SAVED, july3, unitPriceCents = 1000)
        val exported = receipt(2, ReceiptStatus.EXPORTED, july3, unitPriceCents = 3000)
        val items = ReceiptList.withDayTotals(ReceiptList.grouped(listOf(exported, pending)), zone)
        assertEquals(
            listOf(
                ReceiptListItem.Row(pending),
                ReceiptListItem.Row(exported),
                ReceiptListItem.DayTotal(epochDay = LocalDate.of(2026, 7, 3).toEpochDay(), totalCents = 4000),
            ),
            items,
        )
    }
}
