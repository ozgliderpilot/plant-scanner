package com.nursery.scanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nursery.core.DeviceConfig
import com.nursery.core.ReceiptNumbering
import com.nursery.scanner.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {

    val config: StateFlow<DeviceConfig> =
        settings.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeviceConfig.default())

    /** Validate + persist. Returns an error message, or null on success. */
    fun save(prefix: String, url: String, secret: String, intervalSeconds: Int): String? {
        if (!ReceiptNumbering.isValidPrefix(prefix)) return "Device prefix must be exactly two digits"
        if (intervalSeconds < DeviceConfig.MIN_INTERVAL_SECONDS) {
            return "Auto-export interval must be at least ${DeviceConfig.MIN_INTERVAL_SECONDS} seconds"
        }
        viewModelScope.launch {
            settings.saveConfig(
                DeviceConfig(
                    devicePrefix = prefix,
                    endpointUrl = url.trim(),
                    sharedSecret = secret.trim(),
                    autoExportSeconds = intervalSeconds,
                ),
            )
        }
        return null
    }
}
