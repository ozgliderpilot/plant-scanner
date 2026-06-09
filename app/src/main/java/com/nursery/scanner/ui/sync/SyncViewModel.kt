package com.nursery.scanner.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nursery.core.DeviceConfig
import com.nursery.scanner.data.repo.SyncRepository
import com.nursery.scanner.data.repo.SyncResult
import com.nursery.scanner.data.repo.SyncState
import com.nursery.scanner.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SyncViewModel(
    private val sync: SyncRepository,
    settings: SettingsRepository,
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

    fun updatePlantList() = viewModelScope.launch {
        _message.value = describe(sync.updatePlantList(), okWord = "Plant list updated")
    }

    fun exportNow() = viewModelScope.launch {
        _message.value = describe(sync.exportPending(), okWord = "Exported")
    }

    private fun describe(result: SyncResult, okWord: String): String = when (result) {
        is SyncResult.Done -> "$okWord (${result.count})"
        is SyncResult.Error -> "Error: ${result.message}"
        SyncResult.NotConfigured -> "Set up the connection in Settings first"
    }
}
