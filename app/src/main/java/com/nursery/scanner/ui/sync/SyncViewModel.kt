package com.nursery.scanner.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nursery.core.DeviceConfig
import com.nursery.scanner.data.repo.CloudSyncActions
import com.nursery.scanner.data.repo.SyncResult
import com.nursery.scanner.data.repo.SyncState
import com.nursery.scanner.data.settings.SettingsConfigSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SyncViewModel(
    private val sync: CloudSyncActions,
    settings: SettingsConfigSource,
) : ViewModel() {

    val state: StateFlow<SyncState> = sync.state

    val plantCount: StateFlow<Int> =
        sync.plantCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val config: StateFlow<DeviceConfig> =
        settings.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeviceConfig.default())

    /** One-shot result of the last manual action, shown as Done/Error (spec #9). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() {
        _message.value = null
    }

    /** History ↻ and Plants ↻ both run the same cloud sync (export then import). */
    fun syncNow() = viewModelScope.launch {
        _message.value = describe(sync.syncCloud())
    }

    private fun describe(result: SyncResult): String = when (result) {
        is SyncResult.Done -> {
            val base = when {
                result.salesCount == 0 && result.cullCount == 0 -> "Synced (0 pending)"
                result.cullCount == 0 -> "Synced (${result.salesCount} sales)"
                result.salesCount == 0 -> "Synced (${result.cullCount} cull)"
                else -> "Synced (${result.salesCount} sales, ${result.cullCount} cull)"
            }
            result.partialError?.let { "$base · $it" } ?: base
        }
        is SyncResult.Error -> "Error: ${result.message}"
        SyncResult.NotConfigured -> "Set up the connection in Settings first"
    }
}
