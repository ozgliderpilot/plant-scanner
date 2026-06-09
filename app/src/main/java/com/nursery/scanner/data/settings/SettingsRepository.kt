package com.nursery.scanner.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nursery.core.DeviceConfig
import com.nursery.core.ReceiptNumbering
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("settings")

/**
 * Per-device configuration + local counters, persisted with DataStore. Holds the receipt sequence
 * (for `PP-NNN`, #11), the auto-export interval (#10), the Apps Script URL + shared secret, and the
 * last successful sync time (for the status chip).
 */
class SettingsRepository(context: Context) {

    private val store = context.settingsDataStore

    private object Keys {
        val PREFIX = stringPreferencesKey("device_prefix")
        val URL = stringPreferencesKey("endpoint_url")
        val SECRET = stringPreferencesKey("shared_secret")
        val INTERVAL = intPreferencesKey("auto_export_seconds")
        val SEQ = intPreferencesKey("receipt_seq")
        val LAST_SYNCED = longPreferencesKey("last_synced_ms")
    }

    val config: Flow<DeviceConfig> = store.data.map { p ->
        val rawPrefix = p[Keys.PREFIX] ?: DeviceConfig.DEFAULT_PREFIX
        DeviceConfig(
            devicePrefix = if (ReceiptNumbering.isValidPrefix(rawPrefix)) rawPrefix else DeviceConfig.DEFAULT_PREFIX,
            endpointUrl = p[Keys.URL] ?: "",
            sharedSecret = p[Keys.SECRET] ?: "",
            autoExportSeconds = (p[Keys.INTERVAL] ?: DeviceConfig.DEFAULT_INTERVAL_SECONDS)
                .coerceAtLeast(DeviceConfig.MIN_INTERVAL_SECONDS),
        )
    }

    /** Null when never synced. */
    val lastSyncedMs: Flow<Long?> = store.data.map { p -> p[Keys.LAST_SYNCED]?.takeIf { it > 0 } }

    suspend fun saveConfig(config: DeviceConfig) {
        store.edit { p ->
            p[Keys.PREFIX] = config.devicePrefix
            p[Keys.URL] = config.endpointUrl
            p[Keys.SECRET] = config.sharedSecret
            p[Keys.INTERVAL] = config.autoExportSeconds
        }
    }

    /** Atomically increment and return the next local receipt sequence. */
    suspend fun nextReceiptSeq(): Int {
        var next = 1
        store.edit { p ->
            next = (p[Keys.SEQ] ?: 0) + 1
            p[Keys.SEQ] = next
        }
        return next
    }

    suspend fun setLastSynced(epochMs: Long) {
        store.edit { p -> p[Keys.LAST_SYNCED] = epochMs }
    }
}
