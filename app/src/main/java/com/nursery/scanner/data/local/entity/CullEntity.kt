package com.nursery.scanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One locally recorded cull. [status] holds a CullStatus name; it is the sync queue. */
@Entity(tableName = "culls")
data class CullEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val cullNo: String,
    val createdAtEpochMs: Long,
    val status: String,
    val accession: String,
    val name: String,
    @ColumnInfo(name = "plant_group") val group: String?,
    val isUnknown: Boolean,
    val qty: Int,
    val unit: String,
    val reason: String,
    val notes: String?,
)
