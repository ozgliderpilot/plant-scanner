package com.nursery.core

import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class RepotExportTest {

    private val createdAt = Instant.parse("2026-07-01T12:00:00Z").toEpochMilli()

    private val record = RepotRecord(
        localId = 1,
        repotNo = "PP-1751371200-1",
        createdAtEpochMs = createdAt,
        status = RepotStatus.PENDING,
        accession = "31011",
        name = "Acacia pycnantha",
        genus = "Acacia",
        species = "pycnantha",
        cultivar = "",
        commonName = "Golden Wattle",
        group = "Tree",
        tubesBefore = 12,
        potsBefore = 0,
        miscBefore = 0,
        stockBefore = 1,
        tubes = 0,
        pots = 12,
        misc = 0,
        stock = 1,
        tubesForSale = false,
        potsForSale = true,
        miscForSale = false,
    )

    @Test fun `header order matches locked contract`() {
        assertEquals(
            listOf(
                "repot_id", "date", "accession", "name", "genus", "species", "cultivar", "common_name",
                "group",
                "tubes_before", "pots_before", "misc_before", "stock_before",
                "tubes", "pots", "misc", "stock",
                "tubes_for_sale", "pots_for_sale", "misc_for_sale",
            ),
            RepotExport.HEADER,
        )
    }

    @Test fun `row strings include before after counts and for-sale flags`() {
        val row = RepotExport.buildRows(listOf(record), ZoneOffset.UTC).single()
        assertEquals(
            listOf(
                "PP-1751371200-1", "2026-07-01T12:00", "31011", "Acacia pycnantha",
                "Acacia", "pycnantha", "", "Golden Wattle", "Tree",
                "12", "0", "0", "1",
                "0", "12", "0", "1",
                "false", "true", "false",
            ),
            RepotExport.rowAsStrings(row),
        )
    }

    @Test fun `header and row have the same width`() {
        val row = RepotExport.buildRows(listOf(record), ZoneOffset.UTC).single()
        assertEquals(RepotExport.HEADER.size, RepotExport.rowAsStrings(row).size)
    }
}
