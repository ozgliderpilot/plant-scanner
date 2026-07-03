package com.nursery.core

import java.time.Instant
import java.time.ZoneId

data class CullExportRow(
    val cullId: String,
    val isoDate: String,
    val accession: String,
    val name: String,
    val genus: String,
    val species: String,
    val cultivar: String,
    val commonName: String,
    val group: String,
    val qty: Int,
    val unit: SaleUnit,
    val reason: CullReason,
    val notes: String?,
)

/** Builds the cull export payload sent to the Apps Script backend. */
object CullExport {

    val HEADER: List<String> = listOf(
        "cull_id", "date", "accession", "name", "genus", "species", "cultivar", "common_name",
        "group", "qty", "unit", "reason", "notes",
    )

    fun buildRows(culls: List<CullRecord>, zone: ZoneId): List<CullExportRow> =
        culls.map { cull ->
            CullExportRow(
                cullId = cull.cullNo,
                isoDate = Instant.ofEpochMilli(cull.createdAtEpochMs).atZone(zone).toLocalDate().toString(),
                accession = cull.accession,
                name = cull.name,
                genus = cull.genus,
                species = cull.species,
                cultivar = cull.cultivar,
                commonName = cull.commonName,
                group = cull.group.orEmpty(),
                qty = cull.qty,
                unit = cull.unit,
                reason = cull.reason,
                notes = cull.notes,
            )
        }

    fun rowAsStrings(row: CullExportRow): List<String> = listOf(
        row.cullId,
        row.isoDate,
        row.accession,
        row.name,
        row.genus,
        row.species,
        row.cultivar,
        row.commonName,
        row.group,
        row.qty.toString(),
        row.unit.label,
        row.reason.label,
        row.notes.orEmpty(),
    )
}
