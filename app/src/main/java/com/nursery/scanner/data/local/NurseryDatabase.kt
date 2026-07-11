package com.nursery.scanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nursery.scanner.data.local.dao.CullDao
import com.nursery.scanner.data.local.dao.LabelPrintDao
import com.nursery.scanner.data.local.dao.PlantDao
import com.nursery.scanner.data.local.dao.ReceiptDao
import com.nursery.scanner.data.local.entity.CullEntity
import com.nursery.scanner.data.local.entity.LabelPrintEntity
import com.nursery.scanner.data.local.entity.LineItemEntity
import com.nursery.scanner.data.local.entity.PlantEntity
import com.nursery.scanner.data.local.entity.ReceiptEntity

@Database(
    entities = [
        PlantEntity::class,
        ReceiptEntity::class,
        LineItemEntity::class,
        CullEntity::class,
        LabelPrintEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class NurseryDatabase : RoomDatabase() {
    abstract fun plantDao(): PlantDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun cullDao(): CullDao
    abstract fun labelPrintDao(): LabelPrintDao

    companion object {
        const val NAME = "nursery.db"
    }
}
