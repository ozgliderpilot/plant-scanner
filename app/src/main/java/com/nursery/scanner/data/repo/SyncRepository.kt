package com.nursery.scanner.data.repo

import com.nursery.core.Export
import com.nursery.core.ReceiptStatus
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
import java.time.ZoneId

/** Drives the status chip. */
data class SyncState(
    val pendingCount: Int = 0,
    val lastSyncedMs: Long? = null,
    val online: Boolean = false,
    val isBusy: Boolean = false,
    val lastError: String? = null,
)

/** Outcome surfaced to the manual Sync screen (auto-export ignores it / stays silent). */
sealed interface SyncResult {
    data class Done(val count: Int) : SyncResult
    data class Error(val message: String) : SyncResult
    data object NotConfigured : SyncResult
}

/**
 * The single place "talk to the cloud" happens. Both auto-export and the manual "Export now" call
 * [exportPending]; "Update plant list" calls [updatePlantList]. Exported receipts are flipped to
 * EXPORTED only on success — nothing lost, no double counting (spec Sync & Export).
 */
class SyncRepository(
    private val receiptDao: ReceiptDao,
    private val settings: SettingsRepository,
    private val sheets: SheetsClient,
    private val plants: PlantRepository,
    connectivity: ConnectivityObserver,
    scope: CoroutineScope,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val transient = MutableStateFlow(TransientState())

    val state: StateFlow<SyncState> = combine(
        receiptDao.observePendingCount(ReceiptStatus.SAVED.name),
        settings.lastSyncedMs,
        connectivity.online,
        transient,
    ) { pending, lastSynced, online, t ->
        SyncState(
            pendingCount = pending,
            lastSyncedMs = lastSynced,
            online = online,
            isBusy = t.busy,
            lastError = t.error,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), SyncState())

    /** Push pending (SAVED) receipts. Used by both the silent ticker and the manual button. */
    suspend fun exportPending(): SyncResult {
        val config = settings.config.first()
        if (!config.isComplete) return SyncResult.NotConfigured

        val pending = receiptDao.receiptsByStatus(ReceiptStatus.SAVED.name).map { it.toCore() }
        if (pending.isEmpty()) return SyncResult.Done(0)

        transient.update { it.copy(busy = true, error = null) }
        val rows = Export.buildRows(pending, zone).map { Export.rowAsStrings(it) }
        val result = sheets.appendSales(config, Export.HEADER, rows)
        transient.update { it.copy(busy = false) }

        return result.fold(
            onSuccess = {
                receiptDao.markExported(pending.map { r -> r.localId }, ReceiptStatus.EXPORTED.name)
                settings.setLastSynced(System.currentTimeMillis())
                SyncResult.Done(pending.size)
            },
            onFailure = { e ->
                transient.update { it.copy(error = e.message) }
                SyncResult.Error(e.message ?: "Export failed")
            },
        )
    }

    suspend fun updatePlantList(): SyncResult {
        val config = settings.config.first()
        if (!config.isComplete) return SyncResult.NotConfigured
        transient.update { it.copy(busy = true, error = null) }
        val result = plants.updateFromCloud(config)
        transient.update { it.copy(busy = false) }
        return result.fold(
            onSuccess = { SyncResult.Done(it) },
            onFailure = { e ->
                transient.update { it.copy(error = e.message) }
                SyncResult.Error(e.message ?: "Update failed")
            },
        )
    }

    val plantCount: Flow<Int> get() = plants.count

    private data class TransientState(val busy: Boolean = false, val error: String? = null)
}
