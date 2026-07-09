package com.nursery.scanner.ci

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.nursery.core.CullRecord
import com.nursery.core.DeviceConfig
import com.nursery.core.Plant
import com.nursery.core.Receipt
import com.nursery.scanner.data.repo.CullRepository
import com.nursery.scanner.data.repo.PlantRepository
import com.nursery.scanner.data.repo.ReceiptRepository
import com.nursery.scanner.data.settings.SettingsRepository
import com.nursery.scanner.di.AppContainer
import com.nursery.scanner.sync.AutoExportTicker
import java.time.Instant
import java.time.ZoneId

/**
 * Registers the qaDebug CI mode activator before [android.app.Application.onCreate] finishes
 * ContentProvider init. Absent from prod and qaRelease (this file lives in `src/qaDebug` only).
 */
class CiModeInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        CiMode.activator = { container -> activateCiMode(container) }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

private suspend fun activateCiMode(container: AppContainer) {
    val prefs = container.appContext.getSharedPreferences(CI_PREFS, Context.MODE_PRIVATE)
    val orchestrator = CiModeOrchestrator(
        store = PrefsCiSeedStore(prefs),
        plants = PlantRepositorySeeder(container.plantRepository),
        receipts = ReceiptRepositorySeeder(container.receiptRepository),
        culls = CullRepositorySeeder(container.cullRepository),
        settings = SettingsCiWriter(container.settingsRepository),
        ticker = TickerStop(container.autoExportTicker),
        flags = ProcessCiFlags,
    )
    orchestrator.activate()
}

private const val CI_PREFS = "ci_mode"
private const val KEY_SEEDED = "seeded"

private class PrefsCiSeedStore(
    private val prefs: android.content.SharedPreferences,
) : CiSeedStore {
    override var seeded: Boolean
        get() = prefs.getBoolean(KEY_SEEDED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SEEDED, value).commit()
        }
}

private class PlantRepositorySeeder(
    private val plants: PlantRepository,
) : CiPlantSeeder {
    override suspend fun seedPlants(plants: List<Plant>) {
        this.plants.insertAll(plants)
    }
}

private class ReceiptRepositorySeeder(
    private val receipts: ReceiptRepository,
) : CiReceiptSeeder {
    override suspend fun seedReceipt(receipt: Receipt) {
        receipts.insertSeeded(receipt)
    }
}

private class CullRepositorySeeder(
    private val culls: CullRepository,
) : CiCullSeeder {
    override suspend fun seedCull(cull: CullRecord) {
        culls.insertSeeded(cull)
    }
}

private class SettingsCiWriter(
    private val settings: SettingsRepository,
) : CiSettingsWriter {
    override suspend fun saveConfig(config: DeviceConfig) {
        settings.saveConfig(config)
        val epochDay = Instant.ofEpochMilli(CiFixtures.SEED_EPOCH_MS)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
        // Seeded receipt + cull use fixed numbers 99-…-1 and 99-…-2; advance the daily counter
        // through the normal allocator so the walked sale gets the next seq.
        repeat(CiFixtures.SEEDED_SEQ_COUNT) {
            settings.nextReceiptSeq(epochDay)
        }
    }
}

private class TickerStop(
    private val ticker: AutoExportTicker,
) : CiTickerControl {
    override fun stop() = ticker.stop()
}

private object ProcessCiFlags : CiModeFlags {
    override var ciModeActive: Boolean
        get() = CiMode.active
        set(value) {
            CiMode.active = value
        }
    override var useCameraPlaceholder: Boolean
        get() = CiMode.useCameraPlaceholder
        set(value) {
            CiMode.useCameraPlaceholder = value
        }
    override var skipCameraPermission: Boolean
        get() = CiMode.skipCameraPermission
        set(value) {
            CiMode.skipCameraPermission = value
        }
}
