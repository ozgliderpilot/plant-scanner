package com.nursery.scanner.sync

import com.nursery.scanner.data.repo.SyncRepository
import com.nursery.scanner.data.settings.SettingsRepository
import com.nursery.scanner.util.ConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The auto-export "1-minute push" (spec #10). A plain in-app coroutine ticker — NOT WorkManager,
 * whose periodic floor is 15 minutes. Runs while the app is alive; each tick silently pushes pending
 * receipts when configured + online, and swallows every result/error so nothing ever flickers.
 * The manual "Export now" button calls SyncRepository.exportPending() directly and shows its result.
 */
class AutoExportTicker(
    private val sync: SyncRepository,
    private val settings: SettingsRepository,
    private val connectivity: ConnectivityObserver,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val config = settings.config.first()
                if (config.isComplete && connectivity.isOnline()) {
                    runCatching { sync.exportPending() } // silent on success AND failure
                }
                delay(config.autoExportSeconds * 1000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
