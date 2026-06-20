package com.nursery.core

import java.time.Instant
import java.time.ZoneId

/** One spreadsheet row = one line item of one receipt. Includes the transaction date (per spec). */
data class ExportRow(
    val receiptNo: String,
    val isoDate: String,
    val itemSeq: Int,
    val accession: String,
    val name: String,
    val qty: Int,
    val unit: SaleUnit,
    val unitPriceCents: Long,
    val discountPct: Int,
    val lineTotalCents: Long,
)

/**
 * Builds the export payload sent to the Apps Script backend. The backend appends each row and
 * dedupes by receiptNo, so re-sending is harmless — but [Sync.pending] already prevents it.
 */
object Export {

    /** Column order written to the Sheet — keep stable; the Apps Script relies on it. */
    val HEADER: List<String> = listOf(
        "receipt", "date", "item_seq", "accession", "name",
        "qty", "unit", "unit_price", "discount_pct", "line_total",
    )

    fun buildRows(receipts: List<Receipt>, zone: ZoneId): List<ExportRow> =
        receipts.flatMap { receipt ->
            val date = Instant.ofEpochMilli(receipt.createdAtEpochMs).atZone(zone).toLocalDate().toString()
            receipt.lines.map { line ->
                ExportRow(
                    receiptNo = receipt.receiptNo,
                    isoDate = date,
                    itemSeq = line.itemSeq,
                    accession = line.accession,
                    name = line.name,
                    qty = line.qty,
                    unit = line.unit,
                    unitPriceCents = line.unitPriceCents,
                    discountPct = line.discountPct,
                    lineTotalCents = Money.lineTotalCents(line.qty, line.unitPriceCents, line.discountPct),
                )
            }
        }

    /** Row as plain strings in [HEADER] order; prices as plain decimals so Sheets treats them as numbers. */
    fun rowAsStrings(row: ExportRow): List<String> = listOf(
        row.receiptNo,
        row.isoDate,
        row.itemSeq.toString(),
        row.accession,
        row.name,
        row.qty.toString(),
        row.unit.label,
        Money.formatPlain(row.unitPriceCents),
        row.discountPct.toString(),
        Money.formatPlain(row.lineTotalCents),
    )
}
