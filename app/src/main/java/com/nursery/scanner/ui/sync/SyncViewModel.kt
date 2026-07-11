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

    val config: StateFlow<DeviceConfig> =
        settings.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeviceConfig.default())

    /** One-shot result of the last manual action, shown as Done/Error (spec #9). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() {
        _message.value = null
    }

    fun syncNow() = viewModelScope.launch {
        _message.value = describe(sync.syncCloud())
    }

    private fun describe(result: SyncResult): String = when (result) {
        is SyncResult.Done -> {
            val parts = buildList {
                if (result.salesCount > 0) add("${result.salesCount} sales")
                if (result.cullCount > 0) add("${result.cullCount} cull")
                if (result.labelCount > 0) add("${result.labelCount} label")
            }
            val base = if (parts.isEmpty()) "Synced (0 pending)" else "Synced (${parts.joinToString(", ")})"
            withPartial(base, result.partialError)
        }
        is SyncResult.Error -> withPartial("Error: ${result.message}", result.partialError)
        SyncResult.NotConfigured -> "Set up the connection in Settings first"
    }

    private fun withPartial(base: String, partialError: String?): String =
        partialError?.let { "$base · $it" } ?: base
}
