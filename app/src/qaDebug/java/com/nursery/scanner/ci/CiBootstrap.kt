package com.nursery.scanner.ci

import com.nursery.scanner.di.AppContainer

/**
 * qaDebug CI screenshot seed + flags (#72). Always seeds (Maestro clearState); no seed-once prefs.
 */
object CiBootstrap {
    suspend fun activate(container: AppContainer) {
        CiMode.active = true
        container.autoExportTicker.stop()

        val settings = container.settingsRepository
        settings.saveConfig(CiFixtures.deviceConfig())

        container.plantRepository.insertAll(CiFixtures.PLANTS)
        container.receiptRepository.insert(CiFixtures.seededReceipt())
        container.cullRepository.insert(CiFixtures.seededCull())
    }
}
