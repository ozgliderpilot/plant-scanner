package com.nursery.core

/**
 * Policy for the single cloud-sync POST: shape `plantListSync` from the four pending bags,
 * and interpret the one response into per-queue marks plus plant-list import.
 *
 * Android I/O (HTTPS, Room, mutex) stays an adapter. See ADR-0018.
 */
object PlantListSync {

    /**
     * Outgoing payload. Queue keys are row lists in export-header order; omitted when empty.
     * No `header` — the backend uses the four HEADER constants in lockstep with `core/`.
     */
    data class Request(
        val plantListFingerprint: String? = null,
        val sales: List<List<String>>? = null,
        val culls: List<List<String>>? = null,
        val printLabels: List<List<String>>? = null,
        val repots: List<List<String>>? = null,
    )

    /** Nested queue object from a top-level `ok: true` response. */
    data class QueueResult(
        val appended: Int = 0,
        val skipped: Int = 0,
        val error: String? = null,
    )

    data class Response(
        val ok: Boolean,
        val error: String? = null,
        val sales: QueueResult? = null,
        val culls: QueueResult? = null,
        val printLabels: QueueResult? = null,
        val repots: QueueResult? = null,
        val unchanged: Boolean = false,
        val plantListFingerprint: String? = null,
        val plants: List<Plant>? = null,
    )

    data class Decision(
        val markSalesExported: Boolean,
        val markCullsExported: Boolean,
        val markLabelsExported: Boolean,
        val markRepotsExported: Boolean,
        val export: CloudSync.ExportStep,
        val import: PlantListImport.Outcome,
        val outcome: CloudSync.Outcome,
    )

    fun buildRequest(
        salesRows: List<List<String>>,
        cullRows: List<List<String>>,
        printLabelRows: List<List<String>>,
        repotRows: List<List<String>>,
        plantListFingerprint: String?,
    ): Request = Request(
        plantListFingerprint = plantListFingerprint,
        sales = salesRows.takeIf { it.isNotEmpty() },
        culls = cullRows.takeIf { it.isNotEmpty() },
        printLabels = printLabelRows.takeIf { it.isNotEmpty() },
        repots = repotRows.takeIf { it.isNotEmpty() },
    )

    fun interpret(
        sent: Request,
        response: Response,
        pendingSalesCount: Int,
        pendingCullsCount: Int,
        pendingLabelsCount: Int,
        pendingRepotsCount: Int,
    ): Decision {
        val import = if (!response.ok) {
            PlantListImport.decide(
                ok = false,
                unchanged = false,
                plants = null,
                fingerprint = null,
                error = response.error,
            )
        } else {
            PlantListImport.decide(
                ok = true,
                unchanged = response.unchanged,
                plants = response.plants,
                fingerprint = response.plantListFingerprint,
                error = response.error,
            )
        }

        val export = if (!response.ok) {
            CloudSync.ExportStep.Err(response.error?.takeIf { it.isNotBlank() } ?: "Export failed")
        } else {
            exportFromQueues(
                sent = sent,
                response = response,
                pendingSalesCount = pendingSalesCount,
                pendingCullsCount = pendingCullsCount,
                pendingLabelsCount = pendingLabelsCount,
                pendingRepotsCount = pendingRepotsCount,
            )
        }

        val markSales = response.ok && queueSucceeded(sent.sales != null, response.sales)
        val markCulls = response.ok && queueSucceeded(sent.culls != null, response.culls)
        val markLabels = response.ok && queueSucceeded(sent.printLabels != null, response.printLabels)
        val markRepots = response.ok && queueSucceeded(sent.repots != null, response.repots)

        val importStep = when (import) {
            is PlantListImport.Outcome.Err -> CloudSync.ImportStep.Err(import.message)
            else -> CloudSync.ImportStep.Ok
        }

        return Decision(
            markSalesExported = markSales,
            markCullsExported = markCulls,
            markLabelsExported = markLabels,
            markRepotsExported = markRepots,
            export = export,
            import = import,
            outcome = CloudSync.combine(export, importStep),
        )
    }

    private fun exportFromQueues(
        sent: Request,
        response: Response,
        pendingSalesCount: Int,
        pendingCullsCount: Int,
        pendingLabelsCount: Int,
        pendingRepotsCount: Int,
    ): CloudSync.ExportStep {
        val salesOk = queueSucceeded(sent.sales != null, response.sales)
        val cullsOk = queueSucceeded(sent.culls != null, response.culls)
        val labelsOk = queueSucceeded(sent.printLabels != null, response.printLabels)
        val repotsOk = queueSucceeded(sent.repots != null, response.repots)

        val failures = buildList {
            if (sent.sales != null && !salesOk) {
                add(queueError(response.sales, "Sales export failed"))
            }
            if (sent.culls != null && !cullsOk) {
                add(queueError(response.culls, "Cull export failed"))
            }
            if (sent.printLabels != null && !labelsOk) {
                add(queueError(response.printLabels, "Print label export failed"))
            }
            if (sent.repots != null && !repotsOk) {
                add(queueError(response.repots, "Repot export failed"))
            }
        }
        val anySent = sent.sales != null || sent.culls != null ||
            sent.printLabels != null || sent.repots != null
        val anyOk = salesOk || cullsOk || labelsOk || repotsOk

        if (anySent && !anyOk) {
            return CloudSync.ExportStep.Err(failures.firstOrNull() ?: "Export failed")
        }

        return CloudSync.ExportStep.Ok(
            salesCount = if (salesOk) pendingSalesCount else 0,
            cullCount = if (cullsOk) pendingCullsCount else 0,
            labelCount = if (labelsOk) pendingLabelsCount else 0,
            repotCount = if (repotsOk) pendingRepotsCount else 0,
            partialError = failures.takeIf { it.isNotEmpty() }?.joinToString("; "),
        )
    }

    /** True only when this queue was sent and the response object has no error. */
    private fun queueSucceeded(sent: Boolean, result: QueueResult?): Boolean =
        sent && result != null && result.error.isNullOrBlank()

    private fun queueError(result: QueueResult?, fallback: String): String =
        result?.error?.takeIf { it.isNotBlank() } ?: fallback
}
