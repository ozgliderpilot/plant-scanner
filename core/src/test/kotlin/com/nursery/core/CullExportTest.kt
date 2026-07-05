package com.nursery.core

import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class CullExportTest {

    private val createdAt = Instant.parse("2026-07-01T12:00:00Z").toEpochMilli()

    private val known = CullRecord(
        localId = 1,
        cullNo = "PP-1751371200-1",
        createdAtEpochMs = createdAt,
        status = CullStatus.PENDING,
        accession = "31011",
        name = "Acacia pycnantha",
        genus = "Acacia",
        species = "pycnantha",
        cultivar = "",
        commonName = "Golden Wattle",
        group = "Tree",
        isUnknown = false,
        qty = 3,
        unit = SaleUnit.TUBES,
        reason = CullReason.PEST,
        notes = "aphids",
    )

    private val unknown = CullRecord(
        localId = 2,
        cullNo = "PP-1751371200-2",
        createdAtEpochMs = createdAt,
        status = CullStatus.PENDING,
        accession = "9999999999999",
        name = PlantBook.UNKNOWN_NAME,
        genus = "",
        species = "",
        cultivar = "",
        commonName = "",
        group = null,
        isUnknown = true,
        qty = 1,
        unit = SaleUnit.POTS,
        reason = CullReason.DEAD,
        notes = null,
    )

    @Test fun `header order matches spec`() {
        assertEquals(
            listOf(
                "cull_id", "date", "accession", "name", "genus", "species", "cultivar", "common_name",
                "group", "qty", "unit", "reason", "notes",
            ),
            CullExport.HEADER,
        )
    }

    @Test fun `row strings include reason label and blank taxonomic fields for unknown`() {
        val rows = CullExport.buildRows(listOf(known, unknown), ZoneOffset.UTC)
        assertEquals(
            listOf(
                "PP-1751371200-1", "2026-07-01T12:00", "31011", "Acacia pycnantha",
                "Acacia", "pycnantha", "", "Golden Wattle", "Tree",
                "3", "tubes", "Pest", "aphids",
            ),
            CullExport.rowAsStrings(rows[0]),
        )
        assertEquals(
            listOf(
                "PP-1751371200-2", "2026-07-01T12:00", "9999999999999", "unknown",
                "", "", "", "", "",
                "1", "pots", "Dead", "",
            ),
            CullExport.rowAsStrings(rows[1]),
        )
    }

    @Test fun `header and row have the same width`() {
        val row = CullExport.buildRows(listOf(known), ZoneOffset.UTC).single()
        assertEquals(CullExport.HEADER.size, CullExport.rowAsStrings(row).size)
    }
}
