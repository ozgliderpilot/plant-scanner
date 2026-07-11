package com.nursery.core

import java.time.Instant
import java.time.ZoneId

data class LabelPrintExportRow(
    val queueId: String,
    val isoDate: String,
    val accession: String,
    val name: String,
    val copies: Int,
)

/** Builds the PrintQueue export payload sent to the Apps Script backend. */
object LabelPrintExport {

    val HEADER: List<String> = listOf(
        "queue_id", "date", "accession", "name", "copies",
    )

    fun buildRows(requests: List<LabelPrintRequest>, zone: ZoneId): List<LabelPrintExportRow> =
        requests.map { req ->
            LabelPrintExportRow(
                queueId = req.queueId,
                isoDate = Instant.ofEpochMilli(req.createdAtEpochMs).atZone(zone).toLocalDateTime().toString(),
                accession = req.accession,
                name = req.name,
                copies = req.copies,
            )
        }

    fun rowAsStrings(row: LabelPrintExportRow): List<String> = listOf(
        row.queueId,
        row.isoDate,
        row.accession,
        row.name,
        row.copies.toString(),
    )
}
