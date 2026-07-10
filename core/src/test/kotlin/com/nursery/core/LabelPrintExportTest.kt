package com.nursery.core

import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class LabelPrintExportTest {

    private val createdAt = Instant.parse("2026-07-01T12:00:00Z").toEpochMilli()

    private val request = LabelPrintRequest(
        localId = 1,
        queueId = "07-1751371200-1",
        createdAtEpochMs = createdAt,
        status = LabelPrintStatus.PENDING,
        accession = "31011",
        name = "Acacia pycnantha",
        copies = 2,
    )

    @Test fun `header order matches PrintQueue sheet contract`() {
        assertEquals(
            listOf("queue_id", "date", "accession", "name", "copies"),
            LabelPrintExport.HEADER,
        )
    }

    @Test fun `row strings carry queue id confirm time accession name and copies`() {
        val rows = LabelPrintExport.buildRows(listOf(request), ZoneOffset.UTC)
        assertEquals(
            listOf("07-1751371200-1", "2026-07-01T12:00", "31011", "Acacia pycnantha", "2"),
            LabelPrintExport.rowAsStrings(rows.single()),
        )
    }

    @Test fun `header and row have the same width`() {
        val row = LabelPrintExport.buildRows(listOf(request), ZoneOffset.UTC).single()
        assertEquals(LabelPrintExport.HEADER.size, LabelPrintExport.rowAsStrings(row).size)
    }
}
