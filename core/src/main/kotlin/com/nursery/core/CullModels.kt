package com.nursery.core

/** Lifecycle of a cull record from local save to cloud export. */
enum class CullStatus {
    /** Saved locally, pending export to Google Sheets. */
    PENDING,

    /** Confirmed appended to Google Sheets; never re-sent. */
    EXPORTED,
}

/** Why the plant was culled. [label] is shown in the UI and written to the export sheet. */
enum class CullReason(val label: String) {
    DEAD("Dead"),
    POOR_QUALITY("Poor quality"),
    PEST("Pest"),
    DISEASE("Disease"),
    OTHER("Other"),
    ;

    companion object {
        val DEFAULT = DEAD
    }
}

/**
 * One locally recorded cull. [accession] is the scanned/typed code (== barcode). When it matched a
 * plant, [name] is that plant's name; for a not-found "cull as unknown" line the same scanned
 * [accession] is kept and [name] is "unknown".
 */
data class CullRecord(
    val localId: Long,
    val cullNo: String,
    val createdAtEpochMs: Long,
    val status: CullStatus,
    val accession: String,
    val name: String,
    val genus: String = "",
    val species: String = "",
    val cultivar: String = "",
    val commonName: String = "",
    val group: String?,
    val isUnknown: Boolean,
    val qty: Int,
    val unit: SaleUnit,
    val reason: CullReason,
    val notes: String?,
) {
    companion object {
        const val MAX_NOTES_LENGTH = 200

        /** Null when valid; otherwise a short reason the save should be rejected. */
        fun validationError(record: CullRecord): String? = when {
            record.qty < 1 -> "Quantity must be at least 1"
            (record.notes?.length ?: 0) > MAX_NOTES_LENGTH -> "Notes must be at most $MAX_NOTES_LENGTH characters"
            else -> null
        }
    }
}
