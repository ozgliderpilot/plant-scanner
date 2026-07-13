package com.nursery.core

import java.time.Instant
import java.time.ZoneId

data class RepotExportRow(
    val repotId: String,
    val isoDate: String,
    val accession: String,
    val name: String,
    val genus: String,
    val species: String,
    val cultivar: String,
    val commonName: String,
    val group: String,
    val tubesBefore: Int,
    val potsBefore: Int,
    val miscBefore: Int,
    val stockBefore: Int,
    val tubes: Int,
    val pots: Int,
    val misc: Int,
    val stock: Int,
    val tubesForSale: Boolean,
    val potsForSale: Boolean,
    val miscForSale: Boolean,
)

/** Builds the repot export payload sent to the Apps Script backend. */
object RepotExport {

    val HEADER: List<String> = listOf(
        "repot_id", "date", "accession", "name", "genus", "species", "cultivar", "common_name",
        "group",
        "tubes_before", "pots_before", "misc_before", "stock_before",
        "tubes", "pots", "misc", "stock",
        "tubes_for_sale", "pots_for_sale", "misc_for_sale",
    )

    fun buildRows(repots: List<RepotRecord>, zone: ZoneId): List<RepotExportRow> =
        repots.map { repot ->
            RepotExportRow(
                repotId = repot.repotNo,
                isoDate = Instant.ofEpochMilli(repot.createdAtEpochMs).atZone(zone).toLocalDateTime().toString(),
                accession = repot.accession,
                name = repot.name,
                genus = repot.genus,
                species = repot.species,
                cultivar = repot.cultivar,
                commonName = repot.commonName,
                group = repot.group.orEmpty(),
                tubesBefore = repot.tubesBefore,
                potsBefore = repot.potsBefore,
                miscBefore = repot.miscBefore,
                stockBefore = repot.stockBefore,
                tubes = repot.tubes,
                pots = repot.pots,
                misc = repot.misc,
                stock = repot.stock,
                tubesForSale = repot.tubesForSale,
                potsForSale = repot.potsForSale,
                miscForSale = repot.miscForSale,
            )
        }

    fun rowAsStrings(row: RepotExportRow): List<String> = listOf(
        row.repotId,
        row.isoDate,
        row.accession,
        row.name,
        row.genus,
        row.species,
        row.cultivar,
        row.commonName,
        row.group,
        row.tubesBefore.toString(),
        row.potsBefore.toString(),
        row.miscBefore.toString(),
        row.stockBefore.toString(),
        row.tubes.toString(),
        row.pots.toString(),
        row.misc.toString(),
        row.stock.toString(),
        row.tubesForSale.toString(),
        row.potsForSale.toString(),
        row.miscForSale.toString(),
    )
}
