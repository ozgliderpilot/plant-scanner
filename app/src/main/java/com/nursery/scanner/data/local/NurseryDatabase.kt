package com.nursery.scanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nursery.scanner.data.local.dao.PlantDao
import com.nursery.scanner.data.local.dao.ReceiptDao
import com.nursery.scanner.data.local.entity.LineItemEntity
import com.nursery.scanner.data.local.entity.PlantEntity
import com.nursery.scanner.data.local.entity.ReceiptEntity

@Database(
    entities = [PlantEntity::class, ReceiptEntity::class, LineItemEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NurseryDatabase : RoomDatabase() {
    abstract fun plantDao(): PlantDao
    abstract fun receiptDao(): ReceiptDao

    companion object {
        const val NAME = "nursery.db"
    }
}
