package com.nursery.scanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nursery.scanner.data.local.dao.PlantDao
import com.nursery.scanner.data.local.dao.ReceiptDao
import com.nursery.scanner.data.local.entity.LineItemEntity
import com.nursery.scanner.data.local.entity.PlantEntity
import com.nursery.scanner.data.local.entity.ReceiptEntity

@Database(
    entities = [PlantEntity::class, ReceiptEntity::class, LineItemEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class NurseryDatabase : RoomDatabase() {
    abstract fun plantDao(): PlantDao
    abstract fun receiptDao(): ReceiptDao

    companion object {
        const val NAME = "nursery.db"

        /** v1 -> v2: add per-accession stock counts to plants and the sale-unit to line items (additive). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plants ADD COLUMN potsInNursery INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plants ADD COLUMN tubesInNursery INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plants ADD COLUMN miscInNursery INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE line_items ADD COLUMN unit TEXT NOT NULL DEFAULT 'POTS'")
            }
        }
    }
}
