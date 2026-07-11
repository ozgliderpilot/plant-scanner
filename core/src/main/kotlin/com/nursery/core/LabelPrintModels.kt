package com.nursery.core

/** Lifecycle of a label print request from local save to cloud export. */
enum class LabelPrintStatus {
    /** Saved locally, pending export to Google Sheets. */
    PENDING,

    /** Confirmed appended to Google Sheets; never re-sent. */
    EXPORTED,
}

/**
 * One locally recorded **label print request** (reprint of lost/worn labels).
 *
 * [queueId] uses the same `PP-<epochSeconds>-<seq>` allocator as sales and culls.
 * [accession] must already exist in the local plant list — unknown scans are not recorded.
 */
data class LabelPrintRequest(
    val localId: Long,
    val queueId: String,
    val createdAtEpochMs: Long,
    val status: LabelPrintStatus,
    val accession: String,
    val name: String,
    val copies: Int,
) {
    companion object {
        /** Exact copy shown when the scanned/typed accession is not in the local plant list. */
        const val NOT_FOUND_MESSAGE = "Please contact database administrator"

        /** Null when valid; otherwise a short reason the save should be rejected. */
        fun validationError(copies: Int): String? = when {
            copies < 1 -> "Copies must be at least 1"
            else -> null
        }
    }
}
