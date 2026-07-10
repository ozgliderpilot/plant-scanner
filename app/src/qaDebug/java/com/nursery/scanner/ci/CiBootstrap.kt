package com.nursery.scanner.ci

import com.nursery.scanner.di.AppContainer
import java.time.Instant
import java.time.ZoneId

/**
 * qaDebug CI screenshot seed + flags (#72). Always seeds (Maestro clearState); no seed-once prefs.
 */
object CiBootstrap {
    suspend fun activate(container: AppContainer) {
        CiMode.active = true
        container.autoExportTicker.stop()

        val settings = container.settingsRepository
        settings.saveConfig(CiFixtures.deviceConfig())

        // Advance daily seq past seeded receipt (…-1) and cull (…-2) so the walked sale gets …-3.
        val epochDay = Instant.ofEpochMilli(CiFixtures.SEED_EPOCH_MS)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
        repeat(CiFixtures.SEEDED_SEQ_COUNT) {
            settings.nextReceiptSeq(epochDay)
        }

        container.plantRepository.insertAll(CiFixtures.PLANTS)
        container.receiptRepository.insert(CiFixtures.seededReceipt())
        container.cullRepository.insert(CiFixtures.seededCull())
    }
}
