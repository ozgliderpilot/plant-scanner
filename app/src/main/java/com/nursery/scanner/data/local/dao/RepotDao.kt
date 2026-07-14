package com.nursery.scanner.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nursery.scanner.data.local.entity.RepotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RepotDao {

    @Insert
    suspend fun insert(repot: RepotEntity): Long

    @Query("SELECT * FROM repots ORDER BY createdAtEpochMs DESC")
    fun observeRepots(): Flow<List<RepotEntity>>

    @Query("SELECT * FROM repots WHERE status = :status ORDER BY localId")
    suspend fun repotsByStatus(status: String): List<RepotEntity>

    @Query("SELECT COUNT(*) FROM repots WHERE status = :status")
    fun observePendingCount(status: String): Flow<Int>

    @Query("UPDATE repots SET status = :exportedStatus WHERE localId IN (:ids)")
    suspend fun markExported(ids: List<Long>, exportedStatus: String)

    @Query("DELETE FROM repots WHERE status = :exportedStatus AND createdAtEpochMs < :cutoffMs")
    suspend fun deleteExportedOlderThan(exportedStatus: String, cutoffMs: Long)
}
