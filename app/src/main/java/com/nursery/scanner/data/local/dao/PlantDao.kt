package com.nursery.scanner.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nursery.scanner.data.local.entity.PlantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantDao {

    @Query("SELECT * FROM plants")
    fun observeAll(): Flow<List<PlantEntity>>

    @Query("SELECT COUNT(*) FROM plants")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plants: List<PlantEntity>)

    @Query("DELETE FROM plants")
    suspend fun deleteAll()

    /** Wholesale replace of the cached plant list (the "Update plant list" pull). */
    @Transaction
    suspend fun replaceAll(plants: List<PlantEntity>) {
        deleteAll()
        insertAll(plants)
    }
}
