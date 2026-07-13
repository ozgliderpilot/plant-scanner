package com.nursery.core

/** Lifecycle of a repot record from local save to cloud export. */
enum class RepotStatus {
    /** Saved locally, pending export to Google Sheets. */
    PENDING,

    /** Confirmed appended to Google Sheets; never re-sent. */
    EXPORTED,
}

/**
 * One locally recorded repot (absolute count update for an accession).
 *
 * [repotNo] uses the shared receipt-numbering scheme (`PP-<epochSeconds>-<seq>`).
 * Identity fields are a taxonomic snapshot at save time. Before/after counts cover Tubes / Pots /
 * Misc. / Stock plant; three Ready-for-sale flags cover T/P/M only (Stock plant is never for sale).
 */
data class RepotRecord(
    val localId: Long,
    val repotNo: String,
    val createdAtEpochMs: Long,
    val status: RepotStatus,
    val accession: String,
    val name: String,
    val genus: String = "",
    val species: String = "",
    val cultivar: String = "",
    val commonName: String = "",
    val group: String?,
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
) {
    companion object {
        /**
         * Null when valid; otherwise a short reason the save should be rejected.
         *
         * [initialForSale] is the Ready-for-sale defaults shown when the editor opened — used to
         * detect no-op saves where neither counts nor ticks changed.
         */
        fun validationError(record: RepotRecord, initialForSale: ReadyForSaleFlags): String? {
            val counts = listOf(
                record.tubesBefore, record.potsBefore, record.miscBefore, record.stockBefore,
                record.tubes, record.pots, record.misc, record.stock,
            )
            if (counts.any { it < 0 }) return "Counts cannot be negative"
            if (!hasChanges(record, initialForSale)) return "Nothing changed"
            return null
        }

        private fun hasChanges(record: RepotRecord, initialForSale: ReadyForSaleFlags): Boolean =
            record.tubes != record.tubesBefore ||
                record.pots != record.potsBefore ||
                record.misc != record.miscBefore ||
                record.stock != record.stockBefore ||
                record.tubesForSale != initialForSale.tubes ||
                record.potsForSale != initialForSale.pots ||
                record.miscForSale != initialForSale.misc
    }
}
