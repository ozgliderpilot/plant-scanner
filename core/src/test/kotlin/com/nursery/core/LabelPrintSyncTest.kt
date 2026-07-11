package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LabelPrintSyncTest {

    private fun request(status: LabelPrintStatus) = LabelPrintRequest(
        localId = 1,
        queueId = "07-1",
        createdAtEpochMs = 0,
        status = status,
        accession = "31011",
        name = "Acacia",
        copies = 1,
    )

    @Test fun `pending returns only PENDING requests`() {
        val requests = listOf(request(LabelPrintStatus.PENDING), request(LabelPrintStatus.EXPORTED))
        assertEquals(1, LabelPrintSync.pending(requests).size)
        assertEquals(LabelPrintStatus.PENDING, LabelPrintSync.pending(requests).single().status)
    }

    @Test fun `markExported flips status`() {
        val exported = LabelPrintSync.markExported(request(LabelPrintStatus.PENDING))
        assertEquals(LabelPrintStatus.EXPORTED, exported.status)
    }
}
