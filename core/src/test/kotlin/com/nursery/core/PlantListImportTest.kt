package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PlantListImportTest {

    @Test
    fun `ticker with local plants and stored fingerprint sends that fingerprint`() {
        assertEquals(
            "abc123",
            PlantListImport.fingerprintForRequest(
                forceFullPull = false,
                localPlantCount = 10,
                storedFingerprint = "abc123",
            ),
        )
    }

    @Test
    fun `manual refresh omits fingerprint even when one is stored`() {
        assertNull(
            PlantListImport.fingerprintForRequest(
                forceFullPull = true,
                localPlantCount = 10,
                storedFingerprint = "abc123",
            ),
        )
    }

    @Test
    fun `empty local plant cache omits fingerprint even when one is stored`() {
        assertNull(
            PlantListImport.fingerprintForRequest(
                forceFullPull = false,
                localPlantCount = 0,
                storedFingerprint = "abc123",
            ),
        )
    }

    @Test
    fun `blank stored fingerprint is omitted`() {
        assertNull(
            PlantListImport.fingerprintForRequest(
                forceFullPull = false,
                localPlantCount = 5,
                storedFingerprint = "  ",
            ),
        )
    }

    @Test
    fun `unchanged response keeps the local cache`() {
        val outcome = PlantListImport.decide(
            ok = true,
            unchanged = true,
            plants = null,
            fingerprint = "abc123",
            error = null,
        )
        assertIs<PlantListImport.Outcome.KeepCache>(outcome)
    }

    @Test
    fun `full pull with plants and fingerprint applies`() {
        val plants = listOf(
            Plant(
                accession = "2021-0345",
                name = "Banksia",
                group = null,
                light = null,
                potsForSale = true,
                tubesForSale = false,
                miscForSale = true,
            ),
        )
        val outcome = PlantListImport.decide(
            ok = true,
            unchanged = false,
            plants = plants,
            fingerprint = "new-fp",
            error = null,
        )
        assertEquals(
            PlantListImport.Outcome.Apply(plants = plants, fingerprintToStore = "new-fp"),
            outcome,
        )
        val applied = assertIs<PlantListImport.Outcome.Apply>(outcome).plants.single()
        assertEquals(true, applied.potsForSale)
        assertEquals(false, applied.tubesForSale)
        assertEquals(true, applied.miscForSale)
    }

    @Test
    fun `Plant ForSale flags default to false`() {
        val plant = Plant(accession = "2021-0345", name = "Banksia", group = null, light = null)
        assertEquals(false, plant.potsForSale)
        assertEquals(false, plant.tubesForSale)
        assertEquals(false, plant.miscForSale)
    }

    @Test
    fun `empty plant list with fingerprint applies empty nursery`() {
        val outcome = PlantListImport.decide(
            ok = true,
            unchanged = false,
            plants = emptyList(),
            fingerprint = "empty-fp",
            error = null,
        )
        assertEquals(
            PlantListImport.Outcome.Apply(plants = emptyList(), fingerprintToStore = "empty-fp"),
            outcome,
        )
    }

    @Test
    fun `failed response is an error`() {
        val outcome = PlantListImport.decide(
            ok = false,
            unchanged = false,
            plants = null,
            fingerprint = null,
            error = "Server rejected the request",
        )
        assertEquals(
            PlantListImport.Outcome.Err("Server rejected the request"),
            outcome,
        )
    }

    @Test
    fun `full pull without fingerprint is an error`() {
        val outcome = PlantListImport.decide(
            ok = true,
            unchanged = false,
            plants = emptyList(),
            fingerprint = null,
            error = null,
        )
        assertEquals(
            PlantListImport.Outcome.Err("Missing plant-list fingerprint"),
            outcome,
        )
    }

    @Test
    fun `full pull without plants list is an error`() {
        val outcome = PlantListImport.decide(
            ok = true,
            unchanged = false,
            plants = null,
            fingerprint = "new-fp",
            error = null,
        )
        assertEquals(
            PlantListImport.Outcome.Err("Missing plant list in response"),
            outcome,
        )
    }
}
