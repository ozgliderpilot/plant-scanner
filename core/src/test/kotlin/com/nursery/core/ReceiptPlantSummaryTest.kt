package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ReceiptPlantSummaryTest {

    private fun line(name: String, qty: Int = 1, unit: SaleUnit = SaleUnit.POTS) =
        LineItem(accession = "2021-0001", name = name, qty = qty, unitPriceCents = 100, discountPct = 0, unit = unit)

    @Test fun `fewer than N names shows all and no overflow`() {
        val summary = ReceiptPlantSummary.of(listOf(line("Banksia", qty = 3)))
        assertEquals(listOf("Banksia"), summary.lines.map { it.name })
        assertEquals(0, summary.remaining)
    }

    @Test fun `exactly N names shows all and no overflow`() {
        val summary = ReceiptPlantSummary.of(listOf(line("Banksia"), line("Grevillea"), line("Wattle")))
        assertEquals(listOf("Banksia", "Grevillea", "Wattle"), summary.lines.map { it.name })
        assertEquals(0, summary.remaining)
    }

    @Test fun `more than N names shows first N and the remaining count`() {
        val summary = ReceiptPlantSummary.of(
            listOf(line("Banksia"), line("Grevillea"), line("Wattle"), line("Eucalyptus"), line("Acacia")),
        )
        assertEquals(listOf("Banksia", "Grevillea", "Wattle"), summary.lines.map { it.name })
        assertEquals(2, summary.remaining)
    }

    @Test fun `zero lines shows no lines and no overflow`() {
        val summary = ReceiptPlantSummary.of(emptyList())
        assertEquals(emptyList(), summary.lines)
        assertEquals(0, summary.remaining)
    }

    @Test fun `duplicate names are de-duplicated, preserving first-seen order`() {
        val summary = ReceiptPlantSummary.of(
            listOf(line("Banksia"), line("Banksia"), line("Grevillea")),
        )
        assertEquals(listOf("Banksia", "Grevillea"), summary.lines.map { it.name })
        assertEquals(0, summary.remaining)
    }

    @Test fun `overflow counts distinct names beyond N, not raw lines`() {
        val summary = ReceiptPlantSummary.of(
            listOf(line("Banksia"), line("Banksia"), line("Grevillea"), line("Wattle"), line("Eucalyptus")),
        )
        assertEquals(listOf("Banksia", "Grevillea", "Wattle"), summary.lines.map { it.name })
        assertEquals(1, summary.remaining)
    }

    @Test fun `unknown line name is rendered as-is`() {
        val summary = ReceiptPlantSummary.of(listOf(line("unknown")))
        assertEquals(listOf("unknown"), summary.lines.map { it.name })
        assertEquals(0, summary.remaining)
    }

    @Test fun `accepts a Receipt directly`() {
        val receipt = Receipt(
            localId = 1, receiptNo = "PP-1-1", createdAtEpochMs = 0, status = ReceiptStatus.SAVED,
            lines = listOf(line("Banksia"), line("Grevillea"), line("Wattle"), line("Acacia")),
        )
        val summary = ReceiptPlantSummary.of(receipt)
        assertEquals(listOf("Banksia", "Grevillea", "Wattle"), summary.lines.map { it.name })
        assertEquals(1, summary.remaining)
    }

    // --- per-unit counts (P · T · M) ---

    @Test fun `counts a single unit`() {
        val summary = ReceiptPlantSummary.of(listOf(line("Banksia", qty = 3, unit = SaleUnit.POTS)))
        assertEquals("P 3", summary.lines.single().counts)
    }

    @Test fun `counts render in fixed P T M order regardless of line order`() {
        val summary = ReceiptPlantSummary.of(
            listOf(
                line("Banksia", qty = 1, unit = SaleUnit.MISC),
                line("Banksia", qty = 1, unit = SaleUnit.TUBES),
                line("Banksia", qty = 2, unit = SaleUnit.POTS),
            ),
        )
        assertEquals("P 2 · T 1 · M 1", summary.lines.single().counts)
    }

    @Test fun `qty for the same name and unit is summed across lines`() {
        val summary = ReceiptPlantSummary.of(
            listOf(line("Banksia", qty = 2, unit = SaleUnit.POTS), line("Banksia", qty = 1, unit = SaleUnit.POTS)),
        )
        assertEquals("P 3", summary.lines.single().counts)
    }

    @Test fun `only non-zero units appear`() {
        val summary = ReceiptPlantSummary.of(listOf(line("Banksia", qty = 5, unit = SaleUnit.TUBES)))
        assertEquals("T 5", summary.lines.single().counts)
        assertEquals("M 5", ReceiptPlantSummary.of(listOf(line("X", qty = 5, unit = SaleUnit.MISC))).lines.single().counts)
    }
}
