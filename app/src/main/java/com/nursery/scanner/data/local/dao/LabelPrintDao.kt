package com.nursery.scanner.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nursery.scanner.data.local.entity.LabelPrintEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelPrintDao {

    @Insert
    suspend fun insert(request: LabelPrintEntity): Long

    @Query("SELECT * FROM label_print_requests WHERE status = :status ORDER BY localId")
    suspend fun requestsByStatus(status: String): List<LabelPrintEntity>

    @Query("SELECT COUNT(*) FROM label_print_requests WHERE status = :status")
    fun observePendingCount(status: String): Flow<Int>

    @Query("UPDATE label_print_requests SET status = :exportedStatus WHERE localId IN (:ids)")
    suspend fun markExported(ids: List<Long>, exportedStatus: String)

    @Query("DELETE FROM label_print_requests WHERE status = :exportedStatus AND createdAtEpochMs < :cutoffMs")
    suspend fun deleteExportedOlderThan(exportedStatus: String, cutoffMs: Long)
}
