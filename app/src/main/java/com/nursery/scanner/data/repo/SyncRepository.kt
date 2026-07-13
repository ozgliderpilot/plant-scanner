package com.nursery.scanner.data.repo

import com.nursery.core.CloudSync
import com.nursery.core.CullExport
import com.nursery.core.CullStatus
import com.nursery.core.DeviceConfig
import com.nursery.core.Export
import com.nursery.core.LabelPrintExport
import com.nursery.core.LabelPrintStatus
import com.nursery.core.PlantListImport
import com.nursery.core.ReceiptStatus
import com.nursery.core.Retention
import com.nursery.core.RepotStatus
import com.nursery.scanner.data.local.dao.CullDao
import com.nursery.scanner.data.local.dao.LabelPrintDao
import com.nursery.scanner.data.local.dao.ReceiptDao
import com.nursery.scanner.data.local.dao.RepotDao
import com.nursery.scanner.data.local.toCore
import com.nursery.scanner.data.remote.SheetsClient
import com.nursery.scanner.data.settings.SettingsRepository
import com.nursery.scanner.util.ConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/** Drives the status chip. */
data class SyncState(
    val pendingCount: Int = 0,
    val lastSyncedMs: Long? = null,
    val lastPlantListUpdateMs: Long? = null,
    val online: Boolean = false,
    val isBusy: Boolean = false,
    val lastError: String? = null,
)

/** Outcome surfaced to manual ↻ (the background ticker ignores it / stays silent). */
sealed interface SyncResult {
    data class Done(
        val salesCount: Int,
        val cullCount: Int = 0,
        val labelCount: Int = 0,
        val partialError: String? = null,
    ) : SyncResult
    data class Error(val message: String, val partialError: String? = null) : SyncResult
    data object NotConfigured : SyncResult
}

/** Narrow façade SyncViewModel needs — keeps UI tests free of Room/OkHttp. */
interface CloudSyncActions {
    val state: StateFlow<SyncState>
    /** @param forceFullPull when true (manual ↻), omit the stored fingerprint so the server full-pulls. */
    suspend fun syncCloud(forceFullPull: Boolean = false): SyncResult
}

/**
 * The single place "talk to the cloud" happens. History ↻, Plants ↻, and the background ticker
 * all call [syncCloud]: export the sync queue, then import the plant list. Exported rows flip to
 * EXPORTED only on HTTP success — nothing lost, no double counting.
 */
class SyncRepository(
    private val receiptDao: ReceiptDao,
    private val cullDao: CullDao,
    private val labelPrintDao: LabelPrintDao,
    private val repotDao: RepotDao,
    private val settings: SettingsRepository,
    private val sheets: SheetsClient,
    private val plants: PlantRepository,
    connectivity: ConnectivityObserver,
    scope: CoroutineScope,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
) : CloudSyncActions {
    private val transient = MutableStateFlow(TransientState())

    private val cloudMutex = Mutex()

    private val pendingTotal = combine(
        receiptDao.observePendingCount(ReceiptStatus.SAVED.name),
        cullDao.observePendingCount(CullStatus.PENDING.name),
        labelPrintDao.observePendingCount(LabelPrintStatus.PENDING.name),
        repotDao.observePendingCount(RepotStatus.PENDING.name),
    ) { salesPending, cullsPending, labelsPending, repotsPending ->
        salesPending + cullsPending + labelsPending + repotsPending
    }

    override val state: StateFlow<SyncState> = combine(
        pendingTotal,
        settings.lastSyncedMs,
        settings.lastPlantListUpdateMs,
        connectivity.online,
        transient,
    ) { pendingCount, lastSynced, lastPlantListUpdate, online, t ->
        SyncState(
            pendingCount = pendingCount,
            lastSyncedMs = lastSynced,
            lastPlantListUpdateMs = lastPlantListUpdate,
            online = online,
            isBusy = t.busy,
            lastError = t.error,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), SyncState())

    override suspend fun syncCloud(forceFullPull: Boolean): SyncResult = cloudMutex.withLock {
        val config = settings.config.first()
        if (!config.isComplete) return@withLock SyncResult.NotConfigured

        transient.update { it.copy(busy = true, error = null) }

        val exportStep = exportStep(config)
        val localCount = plants.count.first()
        val storedFingerprint = settings.plantListFingerprint.first()
        val importStep = plants.updateFromCloud(
            config = config,
            forceFullPull = forceFullPull,
            localPlantCount = localCount,
            storedFingerprint = storedFingerprint,
        ).fold(
            onSuccess = { outcome ->
                if (outcome is PlantListImport.Outcome.Apply) {
                    settings.setPlantListFingerprint(outcome.fingerprintToStore)
                }
                CloudSync.ImportStep.Ok
            },
            onFailure = { e -> CloudSync.ImportStep.Err(e.message ?: "Update failed") },
        )
        val outcome = CloudSync.combine(exportStep, importStep)

        if (outcome.advanceExportTimestamp) settings.setLastSynced(now())
        if (outcome.advancePlantListTimestamp) settings.setLastPlantListUpdate(now())

        transient.update { it.copy(busy = false, error = outcome.errorMessage) }

        when (val err = outcome.errorMessage) {
            null -> SyncResult.Done(
                salesCount = outcome.salesCount,
                cullCount = outcome.cullCount,
                labelCount = outcome.labelCount,
                partialError = outcome.partialError,
            )
            else -> SyncResult.Error(err, partialError = outcome.partialError)
        }
    }

    /**
     * Push pending sales, culls, then print labels; then retention GC.
     * Queues are independent on partial failure.
     */
    private suspend fun exportStep(config: DeviceConfig): CloudSync.ExportStep {
        val salesPending = receiptDao.receiptsByStatus(ReceiptStatus.SAVED.name).map { it.toCore() }
        val cullsPending = cullDao.cullsByStatus(CullStatus.PENDING.name).map { it.toCore() }
        val labelsPending = labelPrintDao.requestsByStatus(LabelPrintStatus.PENDING.name).map { it.toCore() }
        if (salesPending.isEmpty() && cullsPending.isEmpty() && labelsPending.isEmpty()) {
            purgeRetained()
            return CloudSync.ExportStep.Ok(salesCount = 0, cullCount = 0, labelCount = 0)
        }

        var salesExported = 0
        var cullsExported = 0
        var labelsExported = 0

        if (salesPending.isNotEmpty()) {
            val rows = Export.buildRows(salesPending, zone).map { Export.rowAsStrings(it) }
            val result = sheets.appendSales(config, Export.HEADER, rows)
            result.fold(
                onSuccess = {
                    receiptDao.markExported(salesPending.map { r -> r.localId }, ReceiptStatus.EXPORTED.name)
                    salesExported = salesPending.size
                },
                onFailure = { e ->
                    return CloudSync.ExportStep.Err(e.message ?: "Export failed")
                },
            )
        }

        if (cullsPending.isNotEmpty()) {
            val rows = CullExport.buildRows(cullsPending, zone).map { CullExport.rowAsStrings(it) }
            val result = sheets.appendCulls(config, CullExport.HEADER, rows)
            result.fold(
                onSuccess = {
                    cullDao.markExported(cullsPending.map { c -> c.localId }, CullStatus.EXPORTED.name)
                    cullsExported = cullsPending.size
                },
                onFailure = { e ->
                    purgeRetained()
                    if (salesExported > 0) {
                        return CloudSync.ExportStep.Ok(
                            salesCount = salesExported,
                            cullCount = 0,
                            labelCount = 0,
                            partialError = "Cull export failed",
                        )
                    }
                    return CloudSync.ExportStep.Err(e.message ?: "Export failed")
                },
            )
        }

        if (labelsPending.isNotEmpty()) {
            val rows = LabelPrintExport.buildRows(labelsPending, zone).map { LabelPrintExport.rowAsStrings(it) }
            val result = sheets.appendPrintLabels(config, LabelPrintExport.HEADER, rows)
            result.fold(
                onSuccess = {
                    labelPrintDao.markExported(
                        labelsPending.map { r -> r.localId },
                        LabelPrintStatus.EXPORTED.name,
                    )
                    labelsExported = labelsPending.size
                },
                onFailure = { e ->
                    purgeRetained()
                    if (salesExported > 0 || cullsExported > 0) {
                        return CloudSync.ExportStep.Ok(
                            salesCount = salesExported,
                            cullCount = cullsExported,
                            labelCount = 0,
                            partialError = "Print label export failed",
                        )
                    }
                    return CloudSync.ExportStep.Err(e.message ?: "Export failed")
                },
            )
        }

        purgeRetained()
        return CloudSync.ExportStep.Ok(
            salesCount = salesExported,
            cullCount = cullsExported,
            labelCount = labelsExported,
        )
    }

    private suspend fun purgeRetained() {
        val cutoff = Retention.purgeCutoffEpochMs(now(), zone)
        receiptDao.deleteExportedOlderThan(ReceiptStatus.EXPORTED.name, cutoff)
        cullDao.deleteExportedOlderThan(CullStatus.EXPORTED.name, cutoff)
        labelPrintDao.deleteExportedOlderThan(LabelPrintStatus.EXPORTED.name, cutoff)
    }

    private data class TransientState(val busy: Boolean = false, val error: String? = null)
}
