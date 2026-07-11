package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LabelPrintSearchTest {

    private val requests = listOf(
        LabelPrintRequest(
            localId = 1, queueId = "PP-100", createdAtEpochMs = 0, status = LabelPrintStatus.PENDING,
            accession = "2021-0345", name = "Banksia", copies = 3,
        ),
        LabelPrintRequest(
            localId = 2, queueId = "PP-200", createdAtEpochMs = 0, status = LabelPrintStatus.EXPORTED,
            accession = "2022-0100", name = "Wattle", copies = 1,
        ),
    )

    private fun ids(result: List<LabelPrintRequest>) = result.map { it.localId }

    @Test fun `blank query returns full list`() {
        assertEquals(requests, LabelPrintSearch.filter(requests, ""))
    }

    @Test fun `matches accession name queue id and copies`() {
        assertEquals(listOf(1L), ids(LabelPrintSearch.filter(requests, "2021-0345")))
        assertEquals(listOf(1L), ids(LabelPrintSearch.filter(requests, "Banksia")))
        assertEquals(listOf(1L), ids(LabelPrintSearch.filter(requests, "PP-100")))
        assertEquals(listOf(1L), ids(LabelPrintSearch.filter(requests, "3")))
    }

    @Test fun `matching is case-insensitive and substring`() {
        assertEquals(listOf(1L), ids(LabelPrintSearch.filter(requests, "banksia")))
        assertEquals(listOf(1L), ids(LabelPrintSearch.filter(requests, "pp-1")))
    }

    @Test fun `preserves input order`() {
        assertEquals(listOf(1L, 2L), ids(LabelPrintSearch.filter(requests, "0")))
    }
}
