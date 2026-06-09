package com.nursery.scanner.data.repo

import com.nursery.core.DeviceConfig
import com.nursery.core.Plant
import com.nursery.core.PlantBook
import com.nursery.scanner.data.local.dao.PlantDao
import com.nursery.scanner.data.local.toCore
import com.nursery.scanner.data.local.toEntity
import com.nursery.scanner.data.remote.SheetsClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The cached plant list: read offline for every scan, refreshed on an explicit manual pull. */
class PlantRepository(
    private val plantDao: PlantDao,
    private val sheets: SheetsClient,
) {
    val plants: Flow<List<Plant>> = plantDao.observeAll().map { list -> list.map { it.toCore() } }

    val plantBook: Flow<PlantBook> = plants.map { PlantBook(it) }

    val count: Flow<Int> = plantDao.observeCount()

    /** Pull the plant list from Sheets and replace the cache wholesale. Returns plant count. */
    suspend fun updateFromCloud(config: DeviceConfig): Result<Int> {
        if (!config.isComplete) return Result.failure(IllegalStateException("Not configured"))
        return sheets.fetchPlants(config).mapCatching { plants ->
            plantDao.replaceAll(plants.map { it.toEntity() })
            plants.size
        }
    }
}
