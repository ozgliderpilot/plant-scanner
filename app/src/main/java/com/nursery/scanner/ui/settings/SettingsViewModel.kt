package com.nursery.scanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nursery.core.DeviceConfig
import com.nursery.core.ReceiptNumbering
import com.nursery.scanner.data.settings.SettingsRepository
import com.nursery.scanner.setup.generateDeviceSecret
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {

    val config: StateFlow<DeviceConfig> =
        settings.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeviceConfig.default())

    /**
     * Validate + persist. Generates a device secret when missing or when the prefix changes
     * (new prefix = new Users-tab claim). Returns an error message, or null on success.
     */
    fun save(prefix: String, url: String, secret: String, intervalSeconds: Int): String? {
        if (!ReceiptNumbering.isValidPrefix(prefix)) return "Device prefix must be exactly two digits"
        if (intervalSeconds < DeviceConfig.MIN_INTERVAL_SECONDS) {
            return "Auto-export interval must be at least ${DeviceConfig.MIN_INTERVAL_SECONDS} seconds"
        }
        viewModelScope.launch {
            val existing = settings.config.first()
            val deviceSecret = when {
                existing.deviceSecret.isBlank() -> generateDeviceSecret()
                existing.devicePrefix != prefix -> generateDeviceSecret()
                else -> existing.deviceSecret
            }
            settings.saveConfig(
                DeviceConfig(
                    devicePrefix = prefix,
                    endpointUrl = url.trim(),
                    sharedSecret = secret.trim(),
                    autoExportSeconds = intervalSeconds,
                    deviceSecret = deviceSecret,
                ),
            )
        }
        return null
    }
}
