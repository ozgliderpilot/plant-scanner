package com.nursery.scanner.ci

import com.nursery.core.CullRecord
import com.nursery.core.DeviceConfig
import com.nursery.core.Plant
import com.nursery.core.Receipt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CI mode seam (#72): seed-once fixtures, CI flags, and ticker stop — exercised through the
 * orchestrator's public [activate] entrypoint with in-memory fakes (no Android types).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CiModeOrchestratorTest {

    @Test
    fun activateSeedsFixturesOnceAndSetsCiFlags() = runTest {
        val store = FakeCiStore()
        val plants = FakePlantSeeder()
        val receipts = FakeReceiptSeeder()
        val culls = FakeCullSeeder()
        val settings = FakeCiSettings()
        val ticker = FakeTicker()
        val flags = FakeCiFlags()

        val orchestrator = CiModeOrchestrator(store, plants, receipts, culls, settings, ticker, flags)

        orchestrator.activate()

        assertTrue(flags.ciModeActive)
        assertTrue(flags.useCameraPlaceholder)
        assertTrue(flags.skipCameraPermission)
        assertTrue(ticker.stopped)
        assertEquals(CiFixtures.DEVICE_PREFIX, settings.saved!!.devicePrefix)
        assertEquals(CiFixtures.ENDPOINT_URL, settings.saved!!.endpointUrl)
        assertEquals(CiFixtures.SHARED_SECRET, settings.saved!!.sharedSecret)
        assertEquals(CiFixtures.ACCESSIONS, plants.seeded.map { it.accession })
        assertEquals(1, receipts.seededCount)
        assertEquals(1, culls.seededCount)
        assertTrue(store.seeded)

        // Second activate must not duplicate rows.
        orchestrator.activate()
        assertEquals(1, plants.seedCalls)
        assertEquals(1, receipts.seedCalls)
        assertEquals(1, culls.seedCalls)
        assertEquals(1, settings.saveCalls)
    }

    @Test
    fun activateIsIdempotentWhenAlreadySeeded() = runTest {
        val store = FakeCiStore().apply { seeded = true }
        val plants = FakePlantSeeder()
        val receipts = FakeReceiptSeeder()
        val culls = FakeCullSeeder()
        val settings = FakeCiSettings()
        val ticker = FakeTicker()
        val flags = FakeCiFlags()

        val orchestrator = CiModeOrchestrator(store, plants, receipts, culls, settings, ticker, flags)
        orchestrator.activate()

        assertTrue(flags.ciModeActive)
        assertTrue(ticker.stopped)
        assertEquals(0, plants.seedCalls)
        assertEquals(0, receipts.seedCalls)
        assertEquals(0, culls.seedCalls)
        assertEquals(0, settings.saveCalls)
    }

    @Test
    fun flagsDefaultInactive() {
        val flags = FakeCiFlags()
        assertFalse(flags.ciModeActive)
        assertFalse(flags.useCameraPlaceholder)
        assertFalse(flags.skipCameraPermission)
    }
}

private class FakeCiStore : CiSeedStore {
    override var seeded: Boolean = false
}

private class FakePlantSeeder : CiPlantSeeder {
    var seedCalls = 0
    var seeded: List<Plant> = emptyList()

    override suspend fun seedPlants(plants: List<Plant>) {
        seedCalls++
        seeded = plants
    }
}

private class FakeReceiptSeeder : CiReceiptSeeder {
    var seedCalls = 0
    var seededCount = 0

    override suspend fun seedReceipt(receipt: Receipt) {
        seedCalls++
        seededCount++
    }
}

private class FakeCullSeeder : CiCullSeeder {
    var seedCalls = 0
    var seededCount = 0

    override suspend fun seedCull(cull: CullRecord) {
        seedCalls++
        seededCount++
    }
}

private class FakeCiSettings : CiSettingsWriter {
    var saveCalls = 0
    var saved: DeviceConfig? = null

    override suspend fun saveConfig(config: DeviceConfig) {
        saveCalls++
        saved = config
    }
}

private class FakeTicker : CiTickerControl {
    var stopped = false
    override fun stop() {
        stopped = true
    }
}

private class FakeCiFlags : CiModeFlags {
    override var ciModeActive: Boolean = false
    override var useCameraPlaceholder: Boolean = false
    override var skipCameraPermission: Boolean = false
}
