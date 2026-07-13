package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals

class RepotSearchTest {

    private val repots = listOf(
        RepotRecord(
            localId = 1, repotNo = "PP-100", createdAtEpochMs = 0, status = RepotStatus.PENDING,
            accession = "2021-0345", name = "Banksia", genus = "Banksia", group = "Proteaceae",
            tubesBefore = 5, potsBefore = 0, miscBefore = 0, stockBefore = 0,
            tubes = 0, pots = 5, misc = 0, stock = 0,
            tubesForSale = false, potsForSale = true, miscForSale = false,
        ),
        RepotRecord(
            localId = 2, repotNo = "PP-200", createdAtEpochMs = 0, status = RepotStatus.EXPORTED,
            accession = "2022-0100", name = "Wattle", genus = "Acacia", group = "Fabaceae",
            tubesBefore = 1, potsBefore = 0, miscBefore = 0, stockBefore = 0,
            tubes = 0, pots = 1, misc = 0, stock = 0,
            tubesForSale = false, potsForSale = true, miscForSale = false,
        ),
    )

    private fun ids(result: List<RepotRecord>) = result.map { it.localId }

    @Test fun `blank query returns full list`() {
        assertEquals(repots, RepotSearch.filter(repots, ""))
    }

    @Test fun `matches accession name genus group and repot id`() {
        assertEquals(listOf(1L), ids(RepotSearch.filter(repots, "2021-0345")))
        assertEquals(listOf(1L), ids(RepotSearch.filter(repots, "Banksia")))
        assertEquals(listOf(1L), ids(RepotSearch.filter(repots, "Proteaceae")))
        assertEquals(listOf(1L), ids(RepotSearch.filter(repots, "PP-100")))
        assertEquals(listOf(2L), ids(RepotSearch.filter(repots, "Acacia")))
    }

    @Test fun `matching is case-insensitive and substring`() {
        assertEquals(listOf(1L), ids(RepotSearch.filter(repots, "banksia")))
        assertEquals(listOf(1L), ids(RepotSearch.filter(repots, "rotea")))
        assertEquals(listOf(1L), ids(RepotSearch.filter(repots, "pp-1")))
    }

    @Test fun `preserves input order`() {
        assertEquals(listOf(1L, 2L), ids(RepotSearch.filter(repots, "0")))
    }
}
