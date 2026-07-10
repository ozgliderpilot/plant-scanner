package com.nursery.scanner.data.repo

import com.nursery.core.CullExport
import com.nursery.core.CullStatus
import com.nursery.core.Export
import com.nursery.core.LabelPrintExport
import com.nursery.core.LabelPrintStatus
import com.nursery.core.ReceiptStatus
import com.nursery.core.Retention
import com.nursery.scanner.data.local.dao.CullDao
import com.nursery.scanner.data.local.dao.LabelPrintDao
import com.nursery.scanner.data.local.dao.ReceiptDao
import com.nursery.scanner.data.local.toCore
import com.nursery.scanner.data.remote.SheetsClient
import com.nursery.scanner.data.settings.SettingsRepository
import com.nursery.scanner.util.ConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
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

/** Outcome surfaced to the manual Sync screen (auto-export ignores it / stays silent). */
sealed interface SyncResult {
    data class Done(
        val salesCount: Int,
        val cullCount: Int = 0,
        val labelCount: Int = 0,
        val partialError: String? = null,
    ) : SyncResult {
        val count: Int get() = salesCount + cullCount + labelCount
    }
    data class Error(val message: String) : SyncResult
    data object NotConfigured : SyncResult
}

/**
 * The single place "talk to the cloud" happens. Both auto-export and the manual "Export now" call
 * [exportPending]; "Update plant list" calls [updatePlantList]. Exported rows are flipped to
 * EXPORTED only on success — nothing lost, no double counting (spec Sync & Export).
 */
class SyncRepository(
    private val receiptDao: ReceiptDao,
    private val cullDao: CullDao,
    private val labelPrintDao: LabelPrintDao,
    private val settings: SettingsRepository,
    private val sheets: SheetsClient,
    private val plants: PlantRepository,
    connectivity: ConnectivityObserver,
    scope: CoroutineScope,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val transient = MutableStateFlow(TransientState())

    private val cloudMutex = Mutex()

    val state: StateFlow<SyncState> = combine(
        combine(
            combine(
                receiptDao.observePendingCount(ReceiptStatus.SAVED.name),
                cullDao.observePendingCount(CullStatus.PENDING.name),
            ) { salesPending, cullsPending -> salesPending to cullsPending },
            labelPrintDao.observePendingCount(LabelPrintStatus.PENDING.name),
        ) { salesCulls, labelsPending ->
            Triple(salesCulls.first, salesCulls.second, labelsPending)
        },
        settings.lastSyncedMs,
        settings.lastPlantListUpdateMs,
        connectivity.online,
        transient,
    ) { pending, lastSynced, lastPlantListUpdate, online, t ->
        SyncState(
            pendingCount = pending.first + pending.second + pending.third,
            lastSyncedMs = lastSynced,
            lastPlantListUpdateMs = lastPlantListUpdate,
            online = online,
            isBusy = t.busy,
            lastError = t.error,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), SyncState())

    /** Push pending sales, culls, then print labels; then retention GC. Queues are independent on partial failure. */
    suspend fun exportPending(): SyncResult = cloudMutex.withLock {
        val config = settings.config.first()
        if (!config.isComplete) return@withLock SyncResult.NotConfigured

        val salesPending = receiptDao.receiptsByStatus(ReceiptStatus.SAVED.name).map { it.toCore() }
        val cullsPending = cullDao.cullsByStatus(CullStatus.PENDING.name).map { it.toCore() }
        val labelsPending = labelPrintDao.requestsByStatus(LabelPrintStatus.PENDING.name).map { it.toCore() }
        if (salesPending.isEmpty() && cullsPending.isEmpty() && labelsPending.isEmpty()) {
            purgeRetained()
            return@withLock SyncResult.Done(0, 0, 0)
        }

        transient.update { it.copy(busy = true, error = null) }
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
                    transient.update { it.copy(busy = false, error = e.message) }
                    return@withLock SyncResult.Error(e.message ?: "Export failed")
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
                    transient.update { it.copy(busy = false, error = e.message) }
                    purgeRetained()
                    if (salesExported > 0) {
                        settings.setLastSynced(now())
                        return@withLock SyncResult.Done(
                            salesExported,
                            0,
                            0,
                            partialError = "Cull export failed",
                        )
                    }
                    return@withLock SyncResult.Error(e.message ?: "Export failed")
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
                    transient.update { it.copy(busy = false, error = e.message) }
                    purgeRetained()
                    if (salesExported > 0 || cullsExported > 0) {
                        settings.setLastSynced(now())
                        return@withLock SyncResult.Done(
                            salesExported,
                            cullsExported,
                            0,
                            partialError = "Print label export failed",
                        )
                    }
                    return@withLock SyncResult.Error(e.message ?: "Export failed")
                },
            )
        }

        purgeRetained()
        transient.update { it.copy(busy = false) }
        if (salesExported > 0 || cullsExported > 0 || labelsExported > 0) settings.setLastSynced(now())
        SyncResult.Done(salesExported, cullsExported, labelsExported)
    }

    private suspend fun purgeRetained() {
        val cutoff = Retention.purgeCutoffEpochMs(now(), zone)
        receiptDao.deleteExportedOlderThan(ReceiptStatus.EXPORTED.name, cutoff)
        cullDao.deleteExportedOlderThan(CullStatus.EXPORTED.name, cutoff)
        labelPrintDao.deleteExportedOlderThan(LabelPrintStatus.EXPORTED.name, cutoff)
    }

    suspend fun updatePlantList(): SyncResult = cloudMutex.withLock {
        val config = settings.config.first()
        if (!config.isComplete) return@withLock SyncResult.NotConfigured
        transient.update { it.copy(busy = true, error = null) }
        val result = plants.updateFromCloud(config)
        transient.update { it.copy(busy = false) }
        result.fold(
            onSuccess = {
                settings.setLastPlantListUpdate(now())
                SyncResult.Done(it)
            },
            onFailure = { e ->
                transient.update { it.copy(error = e.message) }
                SyncResult.Error(e.message ?: "Update failed")
            },
        )
    }

    val plantCount: Flow<Int> get() = plants.count

    private data class TransientState(val busy: Boolean = false, val error: String? = null)
}
