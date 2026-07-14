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
            export = CloudSync.ExportStep.Ok(salesCount = 2, cullCount = 1),
            import = CloudSync.ImportStep.Ok,
        )
        assertTrue(outcome.advanceExportTimestamp)
        assertTrue(outcome.advancePlantListTimestamp)
        assertNull(outcome.errorMessage)
        assertEquals(2, outcome.salesCount)
        assertEquals(1, outcome.cullCount)
        assertEquals(0, outcome.labelCount)
    }

    @Test
    fun `label count is carried through when export ok`() {
        val outcome = CloudSync.combine(
            export = CloudSync.ExportStep.Ok(salesCount = 1, cullCount = 0, labelCount = 4),
            import = CloudSync.ImportStep.Ok,
        )
        assertEquals(4, outcome.labelCount)
        assertNull(outcome.errorMessage)
    }

    @Test
    fun `empty export queue still advances export timestamp when import ok`() {
        val outcome = CloudSync.combine(
            export = CloudSync.ExportStep.Ok(salesCount = 0, cullCount = 0),
            import = CloudSync.ImportStep.Ok,
        )
        assertTrue(outcome.advanceExportTimestamp)
        assertTrue(outcome.advancePlantListTimestamp)
        assertNull(outcome.errorMessage)
    }

    @Test
    fun `ok export can withhold export timestamp for stubbed pending queues`() {
        val outcome = CloudSync.combine(
            export = CloudSync.ExportStep.Ok(salesCount = 0, cullCount = 0, advanceTimestamp = false),
            import = CloudSync.ImportStep.Ok,
        )
        assertFalse(outcome.advanceExportTimestamp)
        assertTrue(outcome.advancePlantListTimestamp)
        assertNull(outcome.errorMessage)
    }

    @Test
    fun `export failure still allows plant-list timestamp on import success`() {
        val outcome = CloudSync.combine(
            export = CloudSync.ExportStep.Err("sales push failed"),
            import = CloudSync.ImportStep.Ok,
        )
        assertFalse(outcome.advanceExportTimestamp)
        assertTrue(outcome.advancePlantListTimestamp)
        assertEquals("sales push failed", outcome.errorMessage)
    }

    @Test
    fun `import failure still advances export timestamp when export ok`() {
        val outcome = CloudSync.combine(
            export = CloudSync.ExportStep.Ok(salesCount = 3, cullCount = 0),
            import = CloudSync.ImportStep.Err("plant pull failed"),
        )
        assertTrue(outcome.advanceExportTimestamp)
        assertFalse(outcome.advancePlantListTimestamp)
        assertEquals("plant pull failed", outcome.errorMessage)
        assertEquals(3, outcome.salesCount)
    }

    @Test
    fun `when both fail prefer the export error`() {
        val outcome = CloudSync.combine(
            export = CloudSync.ExportStep.Err("export down"),
            import = CloudSync.ImportStep.Err("import down"),
        )
        assertFalse(outcome.advanceExportTimestamp)
        assertFalse(outcome.advancePlantListTimestamp)
        assertEquals("export down", outcome.errorMessage)
    }

    @Test
    fun `export partial error is kept when import ok`() {
        val outcome = CloudSync.combine(
            export = CloudSync.ExportStep.Ok(salesCount = 2, cullCount = 0, partialError = "Cull export failed"),
            import = CloudSync.ImportStep.Ok,
        )
        assertTrue(outcome.advanceExportTimestamp)
        assertTrue(outcome.advancePlantListTimestamp)
        assertNull(outcome.errorMessage)
        assertEquals("Cull export failed", outcome.partialError)
    }

    @Test
    fun `import failure wins over export partial error for overall failure`() {
        val outcome = CloudSync.combine(
            export = CloudSync.ExportStep.Ok(salesCount = 1, cullCount = 0, partialError = "Cull export failed"),
            import = CloudSync.ImportStep.Err("plant pull failed"),
        )
        assertTrue(outcome.advanceExportTimestamp)
        assertFalse(outcome.advancePlantListTimestamp)
        assertEquals("plant pull failed", outcome.errorMessage)
        assertEquals("Cull export failed", outcome.partialError)
    }
}
