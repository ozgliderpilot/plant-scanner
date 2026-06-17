package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlantBookTest {

    private val book = PlantBook(
        listOf(
            Plant(accession = "2021-0345", name = "Banksia", group = "Proteaceae", light = "Full sun"),
            Plant(accession = "2022-0100", name = "Wattle", group = "Fabaceae", light = "Part shade"),
        ),
    )

    @Test fun `finds by accession`() {
        assertEquals("Banksia", book.findByScan("2021-0345")?.name)
    }

    @Test fun `miss returns null`() {
        assertNull(book.findByScan("does-not-exist"))
    }

    @Test fun `toLine carries accession, name, qty and unit`() {
        val plant = book.findByScan("2021-0345")!!
        val line = book.toLine(plant, qty = 2, unitPriceCents = 1500, discountPct = 10, unit = SaleUnit.TUBES)
        assertEquals("2021-0345", line.accession)
        assertEquals("Banksia", line.name)
        assertEquals(2, line.qty)
        assertEquals(1500, line.unitPriceCents)
        assertEquals(10, line.discountPct)
        assertEquals(SaleUnit.TUBES, line.unit)
        assertFalse(PlantBook.isUnknown(line))
    }

    @Test fun `unknown line keeps the scanned code as accession and is named unknown`() {
        val line = PlantBook.toUnknownLine(code = "9999999999999", qty = 1, unitPriceCents = 800, discountPct = 0, unit = SaleUnit.MISC)
        assertEquals("9999999999999", line.accession)
        assertEquals(PlantBook.UNKNOWN_NAME, line.name)
        assertEquals(1, line.qty)
        assertEquals(SaleUnit.MISC, line.unit)
        assertTrue(PlantBook.isUnknown(line))
    }
}
