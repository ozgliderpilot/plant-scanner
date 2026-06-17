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
