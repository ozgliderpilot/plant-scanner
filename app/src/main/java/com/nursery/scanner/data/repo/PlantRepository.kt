package com.nursery.scanner.data.repo

import com.nursery.core.Plant
import com.nursery.core.PlantBook
import com.nursery.core.PlantListImport
import com.nursery.scanner.data.local.dao.PlantDao
import com.nursery.scanner.data.local.toCore
import com.nursery.scanner.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PlantBookSource {
    val plantBook: Flow<PlantBook>
}

/** The cached plant list: read offline for every scan, refreshed on cloud sync. */
class PlantRepository(
    private val plantDao: PlantDao,
) : PlantBookSource {
    val plants: Flow<List<Plant>> = plantDao.observeAll().map { list -> list.map { it.toCore() } }

    override val plantBook: Flow<PlantBook> = plants.map { PlantBook(it) }

    val count: Flow<Int> = plantDao.observeCount()

    /**
     * Apply a [PlantListImport] decision from `plantListSync`. Replaces the local cache only on
     * [PlantListImport.Outcome.Apply]; caller persists the fingerprint.
     */
    suspend fun applyImport(outcome: PlantListImport.Outcome) {
        if (outcome is PlantListImport.Outcome.Apply) {
            plantDao.replaceAll(outcome.plants.map { it.toEntity() })
        }
    }

    /** Insert plants without wiping the cache. */
    suspend fun insertAll(plants: List<Plant>) {
        plantDao.insertAll(plants.map { it.toEntity() })
    }
}
