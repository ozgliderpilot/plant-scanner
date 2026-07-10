package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudSyncTest {

    @Test
    fun `both steps ok advances both timestamps and has no error`() {
        val outcome = CloudSync.combine(
            export = CloudSync.Step.Ok(salesCount = 2, cullCount = 1),
            import = CloudSync.Step.Ok(plantCount = 50),
        )
        assertTrue(outcome.advanceExportTimestamp)
        assertTrue(outcome.advancePlantListTimestamp)
        assertNull(outcome.errorMessage)
        assertEquals(2, outcome.salesCount)
        assertEquals(1, outcome.cullCount)
        assertEquals(50, outcome.plantCount)
    }

    @Test
    fun `empty export queue still advances export timestamp when import ok`() {
        val outcome = CloudSync.combine(
            export = CloudSync.Step.Ok(salesCount = 0, cullCount = 0),
            import = CloudSync.Step.Ok(plantCount = 10),
        )
        assertTrue(outcome.advanceExportTimestamp)
        assertTrue(outcome.advancePlantListTimestamp)
        assertNull(outcome.errorMessage)
    }

    @Test
    fun `export failure still allows plant-list timestamp on import success`() {
        val outcome = CloudSync.combine(
            export = CloudSync.Step.Err("sales push failed"),
            import = CloudSync.Step.Ok(plantCount = 10),
        )
        assertFalse(outcome.advanceExportTimestamp)
        assertTrue(outcome.advancePlantListTimestamp)
        assertEquals("sales push failed", outcome.errorMessage)
        assertEquals(10, outcome.plantCount)
    }

    @Test
    fun `import failure still advances export timestamp when export ok`() {
        val outcome = CloudSync.combine(
            export = CloudSync.Step.Ok(salesCount = 3, cullCount = 0),
            import = CloudSync.Step.Err("plant pull failed"),
        )
        assertTrue(outcome.advanceExportTimestamp)
        assertFalse(outcome.advancePlantListTimestamp)
        assertEquals("plant pull failed", outcome.errorMessage)
        assertEquals(3, outcome.salesCount)
    }

    @Test
    fun `when both fail prefer the export error`() {
        val outcome = CloudSync.combine(
            export = CloudSync.Step.Err("export down"),
            import = CloudSync.Step.Err("import down"),
        )
        assertFalse(outcome.advanceExportTimestamp)
        assertFalse(outcome.advancePlantListTimestamp)
        assertEquals("export down", outcome.errorMessage)
    }

    @Test
    fun `export partial error is kept when import ok`() {
        val outcome = CloudSync.combine(
            export = CloudSync.Step.Ok(salesCount = 2, cullCount = 0, partialError = "Cull export failed"),
            import = CloudSync.Step.Ok(plantCount = 5),
        )
        assertTrue(outcome.advanceExportTimestamp)
        assertTrue(outcome.advancePlantListTimestamp)
        assertNull(outcome.errorMessage)
        assertEquals("Cull export failed", outcome.partialError)
    }

    @Test
    fun `import failure wins over export partial error for overall failure`() {
        val outcome = CloudSync.combine(
            export = CloudSync.Step.Ok(salesCount = 1, cullCount = 0, partialError = "Cull export failed"),
            import = CloudSync.Step.Err("plant pull failed"),
        )
        assertTrue(outcome.advanceExportTimestamp)
        assertFalse(outcome.advancePlantListTimestamp)
        assertEquals("plant pull failed", outcome.errorMessage)
        assertEquals("Cull export failed", outcome.partialError)
    }
}
