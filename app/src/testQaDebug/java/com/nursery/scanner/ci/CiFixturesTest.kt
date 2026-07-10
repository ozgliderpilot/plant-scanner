package com.nursery.scanner.ci

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fixture contract for CI mode (#72) — values Maestro and docs depend on.
 */
class CiFixturesTest {

    @Test
    fun fixturesMatchGalleryContract() {
        assertEquals("99", CiFixtures.DEVICE_PREFIX)
        assertEquals("https://ci.invalid/exec", CiFixtures.ENDPOINT_URL)
        assertEquals("ci-secret", CiFixtures.SHARED_SECRET)
        assertEquals(listOf("1001", "1002", "1003"), CiFixtures.ACCESSIONS)
        assertEquals("1001", CiFixtures.WALK_ACCESSION)
        assertEquals(2, CiFixtures.SEEDED_SEQ_COUNT)

        val grevillea = CiFixtures.PLANTS.single { it.accession == "1001" }
        assertEquals(12, grevillea.potsInNursery)
        assertEquals(6, grevillea.tubesInNursery)

        val config = CiFixtures.deviceConfig()
        assertEquals(CiFixtures.DEVICE_PREFIX, config.devicePrefix)
        assertEquals(CiFixtures.ENDPOINT_URL, config.endpointUrl)
        assertEquals(CiFixtures.SHARED_SECRET, config.sharedSecret)

        assertEquals("99-1700000000-1", CiFixtures.seededReceipt().receiptNo)
        val seededLine = CiFixtures.seededReceipt().lines.single()
        assertEquals("1002", seededLine.accession)
        assertEquals(10, seededLine.discountPct)
        assertEquals("99-1700000000-2", CiFixtures.seededCull().cullNo)
        assertEquals("1003", CiFixtures.seededCull().accession)
    }
}
