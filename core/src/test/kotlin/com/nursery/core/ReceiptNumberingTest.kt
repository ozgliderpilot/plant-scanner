package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReceiptNumberingTest {

    @Test fun `formats prefix and sequence`() {
        assertEquals("07-241", ReceiptNumbering("07").format(241))
        assertEquals("00-1", ReceiptNumbering("00").format(1))
    }

    @Test fun `valid prefixes are exactly two digits`() {
        assertTrue(ReceiptNumbering.isValidPrefix("07"))
        assertTrue(ReceiptNumbering.isValidPrefix("00"))
        assertTrue(ReceiptNumbering.isValidPrefix("99"))
        assertFalse(ReceiptNumbering.isValidPrefix("7"))
        assertFalse(ReceiptNumbering.isValidPrefix("123"))
        assertFalse(ReceiptNumbering.isValidPrefix("ab"))
        assertFalse(ReceiptNumbering.isValidPrefix(""))
    }

    @Test fun `constructor rejects bad prefix`() {
        assertFailsWith<IllegalArgumentException> { ReceiptNumbering("7") }
    }

    @Test fun `next increments`() {
        assertEquals(241, ReceiptNumbering("07").next(240))
    }
}
