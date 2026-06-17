package com.nursery.scanner.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One line of a receipt. `accession` is the scanned/typed code; `name` == "unknown" if unmatched (#7). */
@Entity(
    tableName = "line_items",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["localId"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("receiptId")],
)
data class LineItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val accession: String,
    val name: String,
    val qty: Int,
    val unitPriceCents: Long,
    val discountPct: Int,
    val unit: String = "POTS",
)
