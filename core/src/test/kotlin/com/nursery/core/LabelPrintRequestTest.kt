package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LabelPrintRequestTest {

    @Test fun `copies must be at least 1`() {
        assertEquals("Copies must be at least 1", LabelPrintRequest.validationError(0))
        assertNull(LabelPrintRequest.validationError(1))
    }

    @Test fun `copies must not exceed shared backend max`() {
        assertEquals(10_000, LabelPrintRequest.COPIES_MAX)
        assertNull(LabelPrintRequest.validationError(LabelPrintRequest.COPIES_MAX))
        assertEquals(
            "Copies must be at most ${LabelPrintRequest.COPIES_MAX}",
            LabelPrintRequest.validationError(LabelPrintRequest.COPIES_MAX + 1),
        )
    }

    @Test fun `not-found message is exact administrator contact copy`() {
        assertEquals(
            "Please contact database administrator",
            LabelPrintRequest.NOT_FOUND_MESSAGE,
        )
    }
}
