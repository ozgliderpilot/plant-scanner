package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CullSearchTest {

    private val culls = listOf(
        CullRecord(
            localId = 1, cullNo = "PP-1", createdAtEpochMs = 0, status = CullStatus.PENDING,
            accession = "2021-0345", name = "Banksia", group = "Proteaceae", isUnknown = false,
            qty = 2, unit = SaleUnit.TUBES, reason = CullReason.PEST, notes = "aphids on tips",
        ),
        CullRecord(
            localId = 2, cullNo = "PP-2", createdAtEpochMs = 0, status = CullStatus.EXPORTED,
            accession = "2022-0100", name = "Wattle", group = "Fabaceae", isUnknown = false,
            qty = 1, unit = SaleUnit.POTS, reason = CullReason.DEAD, notes = null,
        ),
    )

    private fun ids(result: List<CullRecord>) = result.map { it.localId }

    @Test fun `blank query returns full list`() {
        assertEquals(culls, CullSearch.filter(culls, ""))
    }

    @Test fun `matches accession name group reason and notes`() {
        assertEquals(listOf(1L), ids(CullSearch.filter(culls, "2021-0345")))
        assertEquals(listOf(1L), ids(CullSearch.filter(culls, "Banksia")))
        assertEquals(listOf(1L), ids(CullSearch.filter(culls, "Proteaceae")))
        assertEquals(listOf(1L), ids(CullSearch.filter(culls, "Pest")))
        assertEquals(listOf(1L), ids(CullSearch.filter(culls, "aphids")))
    }

    @Test fun `matching is case-insensitive and substring`() {
        assertEquals(listOf(1L), ids(CullSearch.filter(culls, "banksia")))
        assertEquals(listOf(1L), ids(CullSearch.filter(culls, "rotea")))
    }

    @Test fun `preserves input order`() {
        assertEquals(listOf(1L, 2L), ids(CullSearch.filter(culls, "0")))
    }
}
