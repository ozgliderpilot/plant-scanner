package com.nursery.scanner.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A receipt = one customer (spec #4). [status] holds a ReceiptStatus name; it is the sync queue. */
@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val receiptNo: String,
    val createdAtEpochMs: Long,
    val status: String,
)
