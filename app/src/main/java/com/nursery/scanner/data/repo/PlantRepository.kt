package com.nursery.scanner.data.repo

import com.nursery.core.DeviceConfig
import com.nursery.core.Plant
import com.nursery.core.PlantBook
import com.nursery.core.PlantListImport
import com.nursery.scanner.data.local.dao.PlantDao
import com.nursery.scanner.data.local.toCore
import com.nursery.scanner.data.local.toEntity
import com.nursery.scanner.data.remote.SheetsClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PlantBookSource {
    val plantBook: Flow<PlantBook>
}

/** The cached plant list: read offline for every scan, refreshed on cloud sync. */
class PlantRepository(
    private val plantDao: PlantDao,
    private val sheets: SheetsClient,
) : PlantBookSource {
    val plants: Flow<List<Plant>> = plantDao.observeAll().map { list -> list.map { it.toCore() } }

    override val plantBook: Flow<PlantBook> = plants.map { PlantBook(it) }

    val count: Flow<Int> = plantDao.observeCount()

    /**
     * Conditional plant-list import. Returns [PlantListImport.Outcome.Apply] only after a successful
     * local replace (caller persists the fingerprint). [PlantListImport.Outcome.KeepCache] when the
     * server reports unchanged.
     */
    suspend fun updateFromCloud(
        config: DeviceConfig,
        forceFullPull: Boolean,
        localPlantCount: Int,
        storedFingerprint: String?,
    ): Result<PlantListImport.Outcome> {
        if (!config.isComplete) return Result.failure(IllegalStateException("Not configured"))
        val requestFingerprint = PlantListImport.fingerprintForRequest(
            forceFullPull = forceFullPull,
            localPlantCount = localPlantCount,
            storedFingerprint = storedFingerprint,
        )
        return sheets.fetchPlants(config, requestFingerprint).mapCatching { wire ->
            when (
                val decision = PlantListImport.decide(
                    ok = wire.ok,
                    unchanged = wire.unchanged,
                    plants = wire.plants,
                    fingerprint = wire.plantListFingerprint,
                    error = wire.error,
                )
            ) {
                is PlantListImport.Outcome.Apply -> {
                    plantDao.replaceAll(decision.plants.map { it.toEntity() })
                    decision
                }
                PlantListImport.Outcome.KeepCache -> decision
                is PlantListImport.Outcome.Err -> error(decision.message)
            }
        }
    }

    /** Insert plants without wiping the cache. */
    suspend fun insertAll(plants: List<Plant>) {
        plantDao.insertAll(plants.map { it.toEntity() })
    }
}
