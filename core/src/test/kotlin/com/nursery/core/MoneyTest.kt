package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {

    @Test fun `plain multiply, no discount`() {
        assertEquals(1000, Money.lineTotalCents(pots = 2, unitPriceCents = 500, discountPct = 0))
    }

    @Test fun `ten percent off ten dollars is nine dollars`() {
        assertEquals(900, Money.lineTotalCents(pots = 1, unitPriceCents = 1000, discountPct = 10))
    }

    @Test fun `net is rounded half up`() {
        // gross 999c, 10% off -> 899.1 -> 899
        assertEquals(899, Money.lineTotalCents(pots = 3, unitPriceCents = 333, discountPct = 10))
        // gross 5c, 50% off -> 2.5 -> 3 (half up)
        assertEquals(3, Money.lineTotalCents(pots = 1, unitPriceCents = 5, discountPct = 50))
    }

    @Test fun `hundred percent discount is zero`() {
        assertEquals(0, Money.lineTotalCents(pots = 5, unitPriceCents = 1000, discountPct = 100))
    }

    @Test fun `zero pots is zero`() {
        assertEquals(0, Money.lineTotalCents(pots = 0, unitPriceCents = 1000, discountPct = 0))
    }

    @Test fun `rejects out of range inputs`() {
        assertFailsWith<IllegalArgumentException> { Money.lineTotalCents(1, 100, -1) }
        assertFailsWith<IllegalArgumentException> { Money.lineTotalCents(1, 100, 101) }
        assertFailsWith<IllegalArgumentException> { Money.lineTotalCents(-1, 100, 0) }
        assertFailsWith<IllegalArgumentException> { Money.lineTotalCents(1, -100, 0) }
    }

    @Test fun `receipt total sums lines`() {
        val lines = listOf(
            LineItem(accession = "A", name = "x", pots = 2, unitPriceCents = 500, discountPct = 0),  // 1000
            LineItem(accession = "B", name = "y", pots = 1, unitPriceCents = 1000, discountPct = 10), // 900
        )
        assertEquals(1900, Money.receiptTotalCents(lines))
    }

    @Test fun `formatAud renders dollars and cents`() {
        assertEquals("\$9.00", Money.formatAud(900))
        assertEquals("\$0.08", Money.formatAud(8))
        assertEquals("\$12.34", Money.formatAud(1234))
        assertEquals("\$0.00", Money.formatAud(0))
    }

    @Test fun `formatPlain has no currency symbol`() {
        assertEquals("9.00", Money.formatPlain(900))
        assertEquals("12.34", Money.formatPlain(1234))
    }
}
