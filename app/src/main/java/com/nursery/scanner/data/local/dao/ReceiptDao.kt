package com.nursery.scanner.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.nursery.scanner.data.local.entity.LineItemEntity
import com.nursery.scanner.data.local.entity.ReceiptEntity
import com.nursery.scanner.data.local.entity.ReceiptWithLines
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {

    @Insert
    suspend fun insertReceipt(receipt: ReceiptEntity): Long

    @Insert
    suspend fun insertLines(lines: List<LineItemEntity>)

    /** Save a finished receipt and its lines atomically; returns the new local id. */
    @Transaction
    suspend fun saveReceipt(receipt: ReceiptEntity, lines: List<LineItemEntity>): Long {
        val id = insertReceipt(receipt)
        insertLines(lines.map { it.copy(receiptId = id) })
        return id
    }

    @Transaction
    @Query("SELECT * FROM receipts ORDER BY createdAtEpochMs DESC")
    fun observeReceipts(): Flow<List<ReceiptWithLines>>

    @Transaction
    @Query("SELECT * FROM receipts WHERE status = :status ORDER BY localId")
    suspend fun receiptsByStatus(status: String): List<ReceiptWithLines>

    @Transaction
    @Query("SELECT * FROM receipts WHERE localId = :id")
    suspend fun receiptById(id: Long): ReceiptWithLines?

    @Query("SELECT COUNT(*) FROM receipts WHERE status = :status")
    fun observePendingCount(status: String): Flow<Int>

    /** Flip the given receipts to EXPORTED in one statement (no double-counting, spec). */
    @Query("UPDATE receipts SET status = :exportedStatus WHERE localId IN (:ids)")
    suspend fun markExported(ids: List<Long>, exportedStatus: String)
}
