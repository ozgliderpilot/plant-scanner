package com.nursery.scanner.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/** A receipt joined with its line items (Room @Relation). */
data class ReceiptWithLines(
    @Embedded val receipt: ReceiptEntity,
    @Relation(parentColumn = "localId", entityColumn = "receiptId")
    val lines: List<LineItemEntity>,
)
