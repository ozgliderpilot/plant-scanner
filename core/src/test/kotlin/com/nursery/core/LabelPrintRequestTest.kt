package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LabelPrintRequestTest {

    private fun request(copies: Int = 1) = LabelPrintRequest(
        localId = 1,
        queueId = "07-1751371200-1",
        createdAtEpochMs = 0,
        status = LabelPrintStatus.PENDING,
        accession = "31011",
        name = "Acacia pycnantha",
        copies = copies,
    )

    @Test fun `copies must be at least 1`() {
        assertEquals("Copies must be at least 1", LabelPrintRequest.validationError(request(copies = 0)))
        assertNull(LabelPrintRequest.validationError(request(copies = 1)))
    }

    @Test fun `not-found message is exact administrator contact copy`() {
        assertEquals(
            "Please contact database administrator",
            LabelPrintRequest.NOT_FOUND_MESSAGE,
        )
    }
}
