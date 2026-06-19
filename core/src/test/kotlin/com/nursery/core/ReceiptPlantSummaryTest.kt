package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ReceiptPlantSummaryTest {

    private fun line(name: String) =
        LineItem(accession = "2021-0001", name = name, qty = 1, unitPriceCents = 100, discountPct = 0)

    @Test fun `fewer than N names shows all and no overflow`() {
        val summary = ReceiptPlantSummary.of(listOf(line("Banksia")))
        assertEquals(listOf("Banksia"), summary.names)
        assertEquals(0, summary.remaining)
    }

    @Test fun `exactly N names shows all and no overflow`() {
        val summary = ReceiptPlantSummary.of(listOf(line("Banksia"), line("Grevillea")))
        assertEquals(listOf("Banksia", "Grevillea"), summary.names)
        assertEquals(0, summary.remaining)
    }

    @Test fun `more than N names shows first N and the remaining count`() {
        val summary = ReceiptPlantSummary.of(
            listOf(line("Banksia"), line("Grevillea"), line("Wattle"), line("Eucalyptus")),
        )
        assertEquals(listOf("Banksia", "Grevillea"), summary.names)
        assertEquals(2, summary.remaining)
    }

    @Test fun `zero lines shows no names and no overflow`() {
        val summary = ReceiptPlantSummary.of(emptyList())
        assertEquals(emptyList(), summary.names)
        assertEquals(0, summary.remaining)
    }

    @Test fun `duplicate names are de-duplicated, preserving first-seen order`() {
        val summary = ReceiptPlantSummary.of(
            listOf(line("Banksia"), line("Banksia"), line("Grevillea")),
        )
        // Two distinct names, both within N, so no overflow.
        assertEquals(listOf("Banksia", "Grevillea"), summary.names)
        assertEquals(0, summary.remaining)
    }

    @Test fun `overflow counts distinct names beyond N, not raw lines`() {
        val summary = ReceiptPlantSummary.of(
            listOf(line("Banksia"), line("Banksia"), line("Grevillea"), line("Wattle")),
        )
        assertEquals(listOf("Banksia", "Grevillea"), summary.names)
        assertEquals(1, summary.remaining)
    }

    @Test fun `unknown line name is rendered as-is`() {
        val summary = ReceiptPlantSummary.of(listOf(line("unknown")))
        assertEquals(listOf("unknown"), summary.names)
        assertEquals(0, summary.remaining)
    }

    @Test fun `accepts a Receipt directly`() {
        val receipt = Receipt(
            localId = 1, receiptNo = "PP-1-1", createdAtEpochMs = 0, status = ReceiptStatus.SAVED,
            lines = listOf(line("Banksia"), line("Grevillea"), line("Wattle")),
        )
        val summary = ReceiptPlantSummary.of(receipt)
        assertEquals(listOf("Banksia", "Grevillea"), summary.names)
        assertEquals(1, summary.remaining)
    }
}
