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

interface SettingsConfigSource {
    val config: Flow<DeviceConfig>
}

/**
 * Per-device configuration + local counters, persisted with DataStore. Holds the receipt sequence
 * (for `PP-NNN`, #11), the auto-export interval (#10), the Apps Script URL + shared secret, and the
 * last successful sync time (for the status chip).
 */
class SettingsRepository(context: Context) : SettingsConfigSource {

    private val store = context.settingsDataStore

    private object Keys {
        val PREFIX = stringPreferencesKey("device_prefix")
        val URL = stringPreferencesKey("endpoint_url")
        val SECRET = stringPreferencesKey("shared_secret")
        val INTERVAL = intPreferencesKey("auto_export_seconds")
        val SEQ = intPreferencesKey("receipt_seq")
        val SEQ_DAY = longPreferencesKey("receipt_seq_day")
        val LAST_SYNCED = longPreferencesKey("last_synced_ms")
        val LAST_PLANT_LIST_UPDATE = longPreferencesKey("last_plant_list_update_ms")
        val PLANT_LIST_FINGERPRINT = stringPreferencesKey("plant_list_fingerprint")
    }

    override val config: Flow<DeviceConfig> = store.data.map { p ->
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

    /** Null when the plant list has never been pulled successfully. */
    val lastPlantListUpdateMs: Flow<Long?> =
        store.data.map { p -> p[Keys.LAST_PLANT_LIST_UPDATE]?.takeIf { it > 0 } }

    /**
     * Last plant-list fingerprint successfully applied to the local cache.
     * Opaque server echo — never computed on device. Null/blank means force a full pull.
     */
    val plantListFingerprint: Flow<String?> =
        store.data.map { p -> p[Keys.PLANT_LIST_FINGERPRINT]?.trim()?.takeIf { it.isNotEmpty() } }

    suspend fun saveConfig(config: DeviceConfig) {
        store.edit { p ->
            p[Keys.PREFIX] = config.devicePrefix
            p[Keys.URL] = config.endpointUrl
            p[Keys.SECRET] = config.sharedSecret
            p[Keys.INTERVAL] = config.autoExportSeconds
        }
    }

    /**
     * Atomically allocate the next per-device receipt sequence, resetting to 1 each new day
     * ([todayEpochDay] is the receipt's local-zone epoch-day). Both the sequence and the day it
     * belongs to are written in the same edit so concurrent saves can't race.
     */
    suspend fun nextReceiptSeq(todayEpochDay: Long): Int {
        var next = 1
        store.edit { p ->
            next = ReceiptNumbering.nextDailySeq(
                lastDayEpochDay = p[Keys.SEQ_DAY],
                lastSeq = p[Keys.SEQ] ?: 0,
                todayEpochDay = todayEpochDay,
            )
            p[Keys.SEQ] = next
            p[Keys.SEQ_DAY] = todayEpochDay
        }
        return next
    }

    suspend fun setLastSynced(epochMs: Long) {
        store.edit { p -> p[Keys.LAST_SYNCED] = epochMs }
    }

    suspend fun setLastPlantListUpdate(epochMs: Long) {
        store.edit { p -> p[Keys.LAST_PLANT_LIST_UPDATE] = epochMs }
    }

    suspend fun setPlantListFingerprint(fingerprint: String) {
        store.edit { p -> p[Keys.PLANT_LIST_FINGERPRINT] = fingerprint }
    }
}
