package com.nursery.scanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One locally recorded repot. [status] holds a RepotStatus name; it is the sync queue. */
@Entity(tableName = "repots")
data class RepotEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val repotNo: String,
    val createdAtEpochMs: Long,
    val status: String,
    val accession: String,
    val name: String,
    val genus: String = "",
    val species: String = "",
    val cultivar: String = "",
    val commonName: String = "",
    @ColumnInfo(name = "plant_group") val group: String?,
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
