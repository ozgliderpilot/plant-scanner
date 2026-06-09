package com.nursery.scanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cached plant row. Replaced wholesale on "Update plant list". No price column (spec #6). */
@Entity(tableName = "plants")
data class PlantEntity(
    // accession == the Code 128 barcode value; there is no separate barcode field.
    @PrimaryKey val accession: String,
    val name: String,
    // "group" is reserved in SQLite; store under a safe column name.
    @ColumnInfo(name = "plant_group") val group: String?,
    val light: String?,
)
