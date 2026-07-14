package com.nursery.scanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nursery.scanner.data.local.dao.CullDao
import com.nursery.scanner.data.local.dao.LabelPrintDao
import com.nursery.scanner.data.local.dao.PlantDao
import com.nursery.scanner.data.local.dao.ReceiptDao
import com.nursery.scanner.data.local.dao.RepotDao
import com.nursery.scanner.data.local.entity.CullEntity
import com.nursery.scanner.data.local.entity.LabelPrintEntity
import com.nursery.scanner.data.local.entity.LineItemEntity
import com.nursery.scanner.data.local.entity.PlantEntity
import com.nursery.scanner.data.local.entity.ReceiptEntity
import com.nursery.scanner.data.local.entity.RepotEntity

@Database(
    entities = [
        PlantEntity::class,
        ReceiptEntity::class,
        LineItemEntity::class,
        CullEntity::class,
        LabelPrintEntity::class,
        RepotEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
abstract class NurseryDatabase : RoomDatabase() {
    abstract fun plantDao(): PlantDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun cullDao(): CullDao
    abstract fun labelPrintDao(): LabelPrintDao
    abstract fun repotDao(): RepotDao

    companion object {
        const val NAME = "nursery.db"
    }
}
