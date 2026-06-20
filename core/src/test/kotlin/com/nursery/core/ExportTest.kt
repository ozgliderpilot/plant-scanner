package com.nursery.core

import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class ExportTest {

    private val createdAt = Instant.parse("2026-06-09T10:00:00Z").toEpochMilli()

    private val receipt = Receipt(
        localId = 1,
        receiptNo = "07-241",
        createdAtEpochMs = createdAt,
        status = ReceiptStatus.SAVED,
        lines = listOf(
            LineItem(accession = "2021-0345", name = "Banksia", qty = 2, unitPriceCents = 1000, discountPct = 10, unit = SaleUnit.TUBES, itemSeq = 1), // 1800
            LineItem(accession = "9999999999999", name = "unknown", qty = 1, unitPriceCents = 500, discountPct = 0, itemSeq = 2), // 500, unit defaults POTS
        ),
    )

    @Test fun `one row per line item with computed total and date`() {
        val rows = Export.buildRows(listOf(receipt), ZoneOffset.UTC)
        assertEquals(2, rows.size)
        assertEquals("2026-06-09", rows[0].isoDate)
        assertEquals(1800, rows[0].lineTotalCents)
        assertEquals(500, rows[1].lineTotalCents)
    }

    @Test fun `row strings follow header order`() {
        val rows = Export.buildRows(listOf(receipt), ZoneOffset.UTC)
        assertEquals(
            listOf("07-241", "2026-06-09", "1", "2021-0345", "Banksia", "2", "tubes", "10.00", "10", "18.00"),
            Export.rowAsStrings(rows[0]),
        )
        // unknown line: the scanned code lives in the accession column, name = "unknown", unit defaults to Pots
        assertEquals(
            listOf("07-241", "2026-06-09", "2", "9999999999999", "unknown", "1", "pots", "5.00", "0", "5.00"),
            Export.rowAsStrings(rows[1]),
        )
    }

    @Test fun `item_seq column sits immediately after date`() {
        assertEquals(
            listOf("receipt", "date", "item_seq", "accession", "name", "qty", "unit", "unit_price", "discount_pct", "line_total"),
            Export.HEADER,
        )
    }

    @Test fun `item_seq is taken from the line, not a positional index`() {
        // Lines carry non-positional seq values (3, 7); the export must surface those, not 1, 2.
        val r = receipt.copy(
            lines = listOf(
                receipt.lines[0].copy(itemSeq = 3),
                receipt.lines[1].copy(itemSeq = 7),
            ),
        )
        val rows = Export.buildRows(listOf(r), ZoneOffset.UTC)
        assertEquals(3, rows[0].itemSeq)
        assertEquals(7, rows[1].itemSeq)
        assertEquals("3", Export.rowAsStrings(rows[0])[2])
        assertEquals("7", Export.rowAsStrings(rows[1])[2])
    }

    @Test fun `header and row have the same width`() {
        val rows = Export.buildRows(listOf(receipt), ZoneOffset.UTC)
        assertEquals(Export.HEADER.size, Export.rowAsStrings(rows[0]).size)
    }
}
