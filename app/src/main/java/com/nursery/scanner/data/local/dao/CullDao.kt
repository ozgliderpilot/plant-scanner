package com.nursery.scanner.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nursery.scanner.data.local.entity.CullEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CullDao {

    @Insert
    suspend fun insert(cull: CullEntity): Long

    @Query("SELECT * FROM culls ORDER BY createdAtEpochMs DESC")
    fun observeCulls(): Flow<List<CullEntity>>

    @Query("SELECT * FROM culls WHERE status = :status ORDER BY localId")
    suspend fun cullsByStatus(status: String): List<CullEntity>

    @Query("SELECT COUNT(*) FROM culls WHERE status = :status")
    fun observePendingCount(status: String): Flow<Int>

    @Query("UPDATE culls SET status = :exportedStatus WHERE localId IN (:ids)")
    suspend fun markExported(ids: List<Long>, exportedStatus: String)

    @Query("DELETE FROM culls WHERE status = :exportedStatus AND createdAtEpochMs < :cutoffMs")
    suspend fun deleteExportedOlderThan(exportedStatus: String, cutoffMs: Long)
}
