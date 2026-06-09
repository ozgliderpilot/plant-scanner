package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReceiptNumberingTest {

    @Test fun `formats prefix, epoch seconds and daily sequence`() {
        assertEquals("07-1718000000-1", ReceiptNumbering("07").format(epochSeconds = 1_718_000_000L, seq = 1))
        assertEquals("00-1718000000-12", ReceiptNumbering("00").format(epochSeconds = 1_718_000_000L, seq = 12))
    }

    @Test fun `format rejects a negative epoch`() {
        assertFailsWith<IllegalArgumentException> { ReceiptNumbering("07").format(epochSeconds = -1L, seq = 1) }
    }

    @Test fun `format rejects a negative sequence`() {
        assertFailsWith<IllegalArgumentException> { ReceiptNumbering("07").format(epochSeconds = 1L, seq = -1) }
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

    @Test fun `daily sequence starts at 1 on the very first receipt`() {
        assertEquals(1, ReceiptNumbering.nextDailySeq(lastDayEpochDay = null, lastSeq = 0, todayEpochDay = 19_888L))
    }

    @Test fun `daily sequence increments within the same day`() {
        assertEquals(6, ReceiptNumbering.nextDailySeq(lastDayEpochDay = 19_888L, lastSeq = 5, todayEpochDay = 19_888L))
    }

    @Test fun `daily sequence resets to 1 on a new day`() {
        assertEquals(1, ReceiptNumbering.nextDailySeq(lastDayEpochDay = 19_888L, lastSeq = 42, todayEpochDay = 19_889L))
    }
}
