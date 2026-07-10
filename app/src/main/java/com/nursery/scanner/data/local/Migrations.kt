package com.nursery.scanner.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations. Headed to production, the local DB must never be silently wiped (no
 * `fallbackToDestructiveMigration`), so every schema bump ships a real migration here.
 */

/**
 * v2 -> v3: add the per-accession `stockInNursery` count to `plants` (issue #3). Additive only —
 * a new NOT NULL integer column defaulting to 0; the plant list is replaced wholesale on the next
 * "Update plant list", so no backfill is needed.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE plants ADD COLUMN stockInNursery INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v4 -> v5: add the `culls` table for locally recorded plant culls (issue #26). Additive only.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS culls (
                localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                cullNo TEXT NOT NULL,
                createdAtEpochMs INTEGER NOT NULL,
                status TEXT NOT NULL,
                accession TEXT NOT NULL,
                name TEXT NOT NULL,
                plant_group TEXT,
                isUnknown INTEGER NOT NULL,
                qty INTEGER NOT NULL,
                unit TEXT NOT NULL,
                reason TEXT NOT NULL,
                notes TEXT
            )
            """.trimIndent(),
        )
    }
}

/**
 * v5 -> v6: taxonomic snapshot columns on plants, line_items, and culls (issue #27). Additive only.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE plants ADD COLUMN genus TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE plants ADD COLUMN species TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE plants ADD COLUMN cultivar TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE plants ADD COLUMN commonName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE line_items ADD COLUMN genus TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE line_items ADD COLUMN species TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE line_items ADD COLUMN cultivar TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE line_items ADD COLUMN commonName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE line_items ADD COLUMN plant_group TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE culls ADD COLUMN genus TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE culls ADD COLUMN species TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE culls ADD COLUMN cultivar TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE culls ADD COLUMN commonName TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v6 -> v7: add receipt-level payment method (issue #56). Additive only — existing receipts default
 * to card.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE receipts ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'CARD'")
    }
}

/**
 * v7 -> v8: add `label_print_requests` for offline-first label reprints (issue #77). Additive only.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS label_print_requests (
                localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                queueId TEXT NOT NULL,
                createdAtEpochMs INTEGER NOT NULL,
                status TEXT NOT NULL,
                accession TEXT NOT NULL,
                name TEXT NOT NULL,
                copies INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}
