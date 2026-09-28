package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlantListSyncTest {

    private val salesRow = listOf("07-1", "2026-08-16T10:00", "1", "31011", "Acacia")
    private val cullRow = listOf("07-2", "2026-08-16T10:00", "31011", "Acacia", "1", "tubes")
    private val labelRow = listOf("07-3", "2026-08-16T10:00", "31011", "Acacia", "2")
    private val repotRow = listOf("07-4", "2026-08-16T10:00", "31011", "Acacia")

    @Test
    fun `empty queues are omitted and fingerprint is passed through`() {
        val request = PlantListSync.buildRequest(
            salesRows = emptyList(),
            cullRows = emptyList(),
            printLabelRows = emptyList(),
            repotRows = emptyList(),
            plantListFingerprint = "abc123",
        )
        assertNull(request.sales)
        assertNull(request.culls)
        assertNull(request.printLabels)
        assertNull(request.repots)
        assertEquals("abc123", request.plantListFingerprint)
    }

    @Test
    fun `null fingerprint is omitted on the request`() {
        val request = PlantListSync.buildRequest(
            salesRows = emptyList(),
            cullRows = emptyList(),
            printLabelRows = emptyList(),
            repotRows = emptyList(),
            plantListFingerprint = null,
        )
        assertNull(request.plantListFingerprint)
    }

    @Test
    fun `non-empty queues are rows only with no header`() {
        val request = PlantListSync.buildRequest(
            salesRows = listOf(salesRow),
            cullRows = listOf(cullRow),
            printLabelRows = listOf(labelRow),
            repotRows = listOf(repotRow),
            plantListFingerprint = "fp",
        )
        assertEquals(listOf(salesRow), request.sales)
        assertEquals(listOf(cullRow), request.culls)
        assertEquals(listOf(labelRow), request.printLabels)
        assertEquals(listOf(repotRow), request.repots)
    }

    @Test
    fun `only the non-empty queues are included`() {
        val request = PlantListSync.buildRequest(
            salesRows = emptyList(),
            cullRows = listOf(cullRow),
            printLabelRows = emptyList(),
            repotRows = emptyList(),
            plantListFingerprint = "fp",
        )
        assertNull(request.sales)
        assertEquals(listOf(cullRow), request.culls)
        assertNull(request.printLabels)
        assertNull(request.repots)
    }

    @Test
    fun `auth failure marks nothing and is an export error`() {
        val sent = PlantListSync.buildRequest(
            salesRows = listOf(salesRow),
            cullRows = emptyList(),
            printLabelRows = emptyList(),
            repotRows = emptyList(),
            plantListFingerprint = "fp",
        )
        val decision = PlantListSync.interpret(
            sent = sent,
            response = PlantListSync.Response(ok = false, error = "Unauthorized"),
            pendingSalesCount = 1,
            pendingCullsCount = 0,
            pendingLabelsCount = 0,
            pendingRepotsCount = 0,
        )
        assertFalse(decision.markSalesExported)
        assertFalse(decision.markCullsExported)
        assertFalse(decision.markLabelsExported)
        assertFalse(decision.markRepotsExported)
        assertEquals(CloudSync.ExportStep.Err("Unauthorized"), decision.export)
        assertEquals(PlantListImport.Outcome.Err("Unauthorized"), decision.import)
        assertFalse(decision.outcome.advanceExportTimestamp)
        assertEquals("Unauthorized", decision.outcome.errorMessage)
    }

    @Test
    fun `successful queue object is marked exported even when import fails`() {
        val sent = PlantListSync.buildRequest(
            salesRows = listOf(salesRow),
            cullRows = emptyList(),
            printLabelRows = emptyList(),
            repotRows = emptyList(),
            plantListFingerprint = null,
        )
        val decision = PlantListSync.interpret(
            sent = sent,
            response = PlantListSync.Response(
                ok = true,
                sales = PlantListSync.QueueResult(appended = 1, skipped = 0),
                unchanged = false,
                plants = null,
                plantListFingerprint = "fp",
            ),
            pendingSalesCount = 2,
            pendingCullsCount = 0,
            pendingLabelsCount = 0,
            pendingRepotsCount = 0,
        )
        assertTrue(decision.markSalesExported)
        assertEquals(
            CloudSync.ExportStep.Ok(salesCount = 2, cullCount = 0, labelCount = 0, repotCount = 0),
            decision.export,
        )
        assertEquals(PlantListImport.Outcome.Err("Missing plant list in response"), decision.import)
        assertTrue(decision.outcome.advanceExportTimestamp)
        assertFalse(decision.outcome.advancePlantListTimestamp)
        assertEquals("Missing plant list in response", decision.outcome.errorMessage)
    }

    @Test
    fun `sales success and culls error marks only sales and keeps a partial error`() {
        val sent = PlantListSync.buildRequest(
            salesRows = listOf(salesRow),
            cullRows = listOf(cullRow),
            printLabelRows = emptyList(),
            repotRows = emptyList(),
            plantListFingerprint = "fp",
        )
        val plants = listOf(Plant(accession = "31011", name = "Acacia", group = null, light = null))
        val decision = PlantListSync.interpret(
            sent = sent,
            response = PlantListSync.Response(
                ok = true,
                sales = PlantListSync.QueueResult(appended = 1, skipped = 0),
                culls = PlantListSync.QueueResult(error = "Cull notes contain unsupported characters"),
                unchanged = false,
                plantListFingerprint = "new-fp",
                plants = plants,
            ),
            pendingSalesCount = 1,
            pendingCullsCount = 1,
            pendingLabelsCount = 0,
            pendingRepotsCount = 0,
        )
        assertTrue(decision.markSalesExported)
        assertFalse(decision.markCullsExported)
        assertEquals(
            CloudSync.ExportStep.Ok(
                salesCount = 1,
                cullCount = 0,
                labelCount = 0,
                repotCount = 0,
                partialError = "Cull notes contain unsupported characters",
            ),
            decision.export,
        )
        assertEquals(
            PlantListImport.Outcome.Apply(plants = plants, fingerprintToStore = "new-fp"),
            decision.import,
        )
        assertTrue(decision.outcome.advanceExportTimestamp)
        assertTrue(decision.outcome.advancePlantListTimestamp)
        assertNull(decision.outcome.errorMessage)
        assertEquals("Cull notes contain unsupported characters", decision.outcome.partialError)
    }

    @Test
    fun `all sent queues failing is an export error and does not mark`() {
        val sent = PlantListSync.buildRequest(
            salesRows = listOf(salesRow),
            cullRows = emptyList(),
            printLabelRows = emptyList(),
            repotRows = emptyList(),
            plantListFingerprint = "fp",
        )
        val decision = PlantListSync.interpret(
            sent = sent,
            response = PlantListSync.Response(
                ok = true,
                sales = PlantListSync.QueueResult(error = "Row width does not match header"),
                unchanged = true,
                plantListFingerprint = "fp",
            ),
            pendingSalesCount = 1,
            pendingCullsCount = 0,
            pendingLabelsCount = 0,
            pendingRepotsCount = 0,
        )
        assertFalse(decision.markSalesExported)
        assertEquals(
            CloudSync.ExportStep.Err("Row width does not match header"),
            decision.export,
        )
        assertIs<PlantListImport.Outcome.KeepCache>(decision.import)
        assertFalse(decision.outcome.advanceExportTimestamp)
        assertTrue(decision.outcome.advancePlantListTimestamp)
    }

    @Test
    fun `missing result for a sent queue is not marked exported`() {
        val sent = PlantListSync.buildRequest(
            salesRows = listOf(salesRow),
            cullRows = emptyList(),
            printLabelRows = emptyList(),
            repotRows = emptyList(),
            plantListFingerprint = "fp",
        )
        val decision = PlantListSync.interpret(
            sent = sent,
            response = PlantListSync.Response(ok = true, unchanged = true, plantListFingerprint = "fp"),
            pendingSalesCount = 1,
            pendingCullsCount = 0,
            pendingLabelsCount = 0,
            pendingRepotsCount = 0,
        )
        assertFalse(decision.markSalesExported)
        assertIs<CloudSync.ExportStep.Err>(decision.export)
    }

    @Test
    fun `empty request with unchanged import keeps cache and advances both timestamps`() {
        val sent = PlantListSync.buildRequest(
            salesRows = emptyList(),
            cullRows = emptyList(),
            printLabelRows = emptyList(),
            repotRows = emptyList(),
            plantListFingerprint = "fp",
        )
        val decision = PlantListSync.interpret(
            sent = sent,
            response = PlantListSync.Response(
                ok = true,
                unchanged = true,
                plantListFingerprint = "fp",
            ),
            pendingSalesCount = 0,
            pendingCullsCount = 0,
            pendingLabelsCount = 0,
            pendingRepotsCount = 0,
        )
        assertFalse(decision.markSalesExported)
        assertEquals(CloudSync.ExportStep.Ok(), decision.export)
        assertIs<PlantListImport.Outcome.KeepCache>(decision.import)
        assertTrue(decision.outcome.advanceExportTimestamp)
        assertTrue(decision.outcome.advancePlantListTimestamp)
        assertNull(decision.outcome.errorMessage)
    }

    @Test
    fun `labels-only success with unchanged still marks labels`() {
        val sent = PlantListSync.buildRequest(
            salesRows = emptyList(),
            cullRows = emptyList(),
            printLabelRows = listOf(labelRow),
            repotRows = emptyList(),
            plantListFingerprint = "fp",
        )
        val decision = PlantListSync.interpret(
            sent = sent,
            response = PlantListSync.Response(
                ok = true,
                printLabels = PlantListSync.QueueResult(appended = 1, skipped = 0),
                unchanged = true,
                plantListFingerprint = "fp",
            ),
            pendingSalesCount = 0,
            pendingCullsCount = 0,
            pendingLabelsCount = 3,
            pendingRepotsCount = 0,
        )
        assertTrue(decision.markLabelsExported)
        assertEquals(
            CloudSync.ExportStep.Ok(salesCount = 0, cullCount = 0, labelCount = 3, repotCount = 0),
            decision.export,
        )
        assertIs<PlantListImport.Outcome.KeepCache>(decision.import)
    }

    @Test
    fun `queue object with error is not marked even when appended is present`() {
        val sent = PlantListSync.buildRequest(
            salesRows = listOf(salesRow),
            cullRows = emptyList(),
            printLabelRows = emptyList(),
            repotRows = emptyList(),
            plantListFingerprint = "fp",
        )
        val decision = PlantListSync.interpret(
            sent = sent,
            response = PlantListSync.Response(
                ok = true,
                sales = PlantListSync.QueueResult(appended = 1, skipped = 0, error = "nope"),
                unchanged = true,
                plantListFingerprint = "fp",
            ),
            pendingSalesCount = 1,
            pendingCullsCount = 0,
            pendingLabelsCount = 0,
            pendingRepotsCount = 0,
        )
        assertFalse(decision.markSalesExported)
        assertIs<CloudSync.ExportStep.Err>(decision.export)
    }
}
