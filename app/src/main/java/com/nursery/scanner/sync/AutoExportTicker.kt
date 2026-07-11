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
 * Background cloud-sync ticker. A plain in-app coroutine — NOT WorkManager, whose periodic floor
 * is 15 minutes. Runs while the app is alive; each tick silently runs [SyncRepository.syncCloud]
 * when configured + online, and swallows every result/error so nothing ever flickers.
 * History/Plants ↻ call the same [SyncRepository.syncCloud] and surface the result.
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
                    runCatching { sync.syncCloud() }
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
