package com.nursery.scanner.di

import android.content.Context
import androidx.room.Room
import com.nursery.scanner.data.local.MIGRATION_2_3
import com.nursery.scanner.data.local.MIGRATION_4_5
import com.nursery.scanner.data.local.NurseryDatabase
import com.nursery.scanner.data.remote.SheetsClient
import com.nursery.scanner.data.repo.CullRepository
import com.nursery.scanner.data.repo.PlantRepository
import com.nursery.scanner.data.repo.ReceiptRepository
import com.nursery.scanner.data.repo.SyncRepository
import com.nursery.scanner.data.settings.SettingsRepository
import com.nursery.scanner.sync.AutoExportTicker
import com.nursery.scanner.util.ConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container (no Hilt — keeps the build simple). Single instance per process. */
class AppContainer(context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val db = Room.databaseBuilder(
        context.applicationContext,
        NurseryDatabase::class.java,
        NurseryDatabase.NAME,
    ).addMigrations(MIGRATION_2_3, MIGRATION_4_5).build()

    private val sheets = SheetsClient()
    private val connectivity = ConnectivityObserver(context.applicationContext)

    val settingsRepository = SettingsRepository(context.applicationContext)
    val plantRepository = PlantRepository(db.plantDao(), sheets)
    val receiptRepository = ReceiptRepository(db.receiptDao(), settingsRepository)
    val cullRepository = CullRepository(db.cullDao(), settingsRepository)
    val syncRepository = SyncRepository(
        receiptDao = db.receiptDao(),
        cullDao = db.cullDao(),
        settings = settingsRepository,
        sheets = sheets,
        plants = plantRepository,
        connectivity = connectivity,
        scope = appScope,
    )

    val autoExportTicker = AutoExportTicker(syncRepository, settingsRepository, connectivity, appScope)

    val viewModelFactory = NurseryViewModelFactory(this)
}
