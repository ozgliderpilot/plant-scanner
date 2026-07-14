package com.nursery.scanner.di

import android.content.Context
import androidx.room.Room
import com.nursery.scanner.data.local.MIGRATION_2_3
import com.nursery.scanner.data.local.MIGRATION_4_5
import com.nursery.scanner.data.local.MIGRATION_5_6
import com.nursery.scanner.data.local.MIGRATION_6_7
import com.nursery.scanner.data.local.MIGRATION_7_8
import com.nursery.scanner.data.local.MIGRATION_8_9
import com.nursery.scanner.data.local.MIGRATION_9_10
import com.nursery.scanner.data.local.NurseryDatabase
import com.nursery.scanner.data.remote.SheetsClient
import com.nursery.scanner.data.repo.CullRepository
import com.nursery.scanner.data.repo.LabelPrintRepository
import com.nursery.scanner.data.repo.PlantRepository
import com.nursery.scanner.data.repo.ReceiptRepository
import com.nursery.scanner.data.repo.RepotRepository
import com.nursery.scanner.data.repo.SyncRepository
import com.nursery.scanner.data.settings.SettingsRepository
import com.nursery.scanner.sync.AutoExportTicker
import com.nursery.scanner.util.ConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container (no Hilt — keeps the build simple). Single instance per process. */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val db = Room.databaseBuilder(
        appContext,
        NurseryDatabase::class.java,
        NurseryDatabase.NAME,
    ).addMigrations(
        MIGRATION_2_3, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        MIGRATION_9_10,
    ).build()

    private val sheets = SheetsClient()
    private val connectivity = ConnectivityObserver(appContext)

    val settingsRepository = SettingsRepository(appContext)
    val plantRepository = PlantRepository(db.plantDao(), sheets)
    val receiptRepository = ReceiptRepository(db.receiptDao(), settingsRepository)
    val cullRepository = CullRepository(db.cullDao(), settingsRepository)
    val labelPrintRepository = LabelPrintRepository(db.labelPrintDao(), settingsRepository)
    val repotRepository = RepotRepository(db.repotDao(), settingsRepository)
    val syncRepository = SyncRepository(
        receiptDao = db.receiptDao(),
        cullDao = db.cullDao(),
        labelPrintDao = db.labelPrintDao(),
        repotDao = db.repotDao(),
        settings = settingsRepository,
        sheets = sheets,
        plants = plantRepository,
        connectivity = connectivity,
        scope = appScope,
    )

    val autoExportTicker = AutoExportTicker(syncRepository, settingsRepository, connectivity, appScope)

    val viewModelFactory = NurseryViewModelFactory(this)
}
