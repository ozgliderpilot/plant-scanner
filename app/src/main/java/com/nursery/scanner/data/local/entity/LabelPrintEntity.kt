package com.nursery.scanner.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One locally recorded label print request. [status] holds a LabelPrintStatus name; it is the sync queue. */
@Entity(tableName = "label_print_requests")
data class LabelPrintEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val queueId: String,
    val createdAtEpochMs: Long,
    val status: String,
    val accession: String,
    val name: String,
    val copies: Int,
)
