package com.nursery.scanner.ci

import com.nursery.core.CullRecord
import com.nursery.core.DeviceConfig
import com.nursery.core.Plant
import com.nursery.core.Receipt

/** Persists whether CI fixtures have already been written (seed-once). */
interface CiSeedStore {
    var seeded: Boolean
}

interface CiPlantSeeder {
    suspend fun seedPlants(plants: List<Plant>)
}

interface CiReceiptSeeder {
    suspend fun seedReceipt(receipt: Receipt)
}

interface CiCullSeeder {
    suspend fun seedCull(cull: CullRecord)
}

interface CiSettingsWriter {
    suspend fun saveConfig(config: DeviceConfig)
}

interface CiTickerControl {
    fun stop()
}

/** Mutable CI UI/runtime flags applied by the orchestrator. */
interface CiModeFlags {
    var ciModeActive: Boolean
    var useCameraPlaceholder: Boolean
    var skipCameraPermission: Boolean
}

/**
 * Single CI mode entrypoint (#72): stop auto-export, set flags, seed fixtures once.
 * Free of Android UI types so unit tests can drive it with fakes.
 */
class CiModeOrchestrator(
    private val store: CiSeedStore,
    private val plants: CiPlantSeeder,
    private val receipts: CiReceiptSeeder,
    private val culls: CiCullSeeder,
    private val settings: CiSettingsWriter,
    private val ticker: CiTickerControl,
    private val flags: CiModeFlags,
) {
    suspend fun activate() {
        flags.ciModeActive = true
        flags.useCameraPlaceholder = true
        flags.skipCameraPermission = true
        ticker.stop()

        if (store.seeded) return

        settings.saveConfig(CiFixtures.deviceConfig())
        plants.seedPlants(CiFixtures.PLANTS)
        receipts.seedReceipt(CiFixtures.seededReceipt())
        culls.seedCull(CiFixtures.seededCull())
        store.seeded = true
    }
}
