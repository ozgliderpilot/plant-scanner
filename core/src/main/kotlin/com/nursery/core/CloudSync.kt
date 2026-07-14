package com.nursery.core

/**
 * Combines the two legs of a **cloud sync**: export the sync queue, then import the plant list.
 *
 * Per-step successes still advance their own timestamps even when the overall result is failure.
 * When both legs fail, the export error wins (queue/money first).
 */
object CloudSync {

    sealed interface ExportStep {
        data class Ok(
            val salesCount: Int = 0,
            val cullCount: Int = 0,
            val labelCount: Int = 0,
            val partialError: String? = null,
            /** False when wired queues are idle but a stubbed queue (e.g. repots) still has PENDING rows. */
            val advanceTimestamp: Boolean = true,
        ) : ExportStep

        data class Err(val message: String) : ExportStep
    }

    sealed interface ImportStep {
        data object Ok : ImportStep
        data class Err(val message: String) : ImportStep
    }

    data class Outcome(
        val salesCount: Int,
        val cullCount: Int,
        val labelCount: Int,
        val advanceExportTimestamp: Boolean,
        val advancePlantListTimestamp: Boolean,
        val errorMessage: String?,
        val partialError: String?,
    )

    fun combine(export: ExportStep, import: ImportStep): Outcome {
        val importOk = import is ImportStep.Ok
        val importErr = when (import) {
            ImportStep.Ok -> null
            is ImportStep.Err -> import.message
        }
        return when (export) {
            is ExportStep.Ok -> Outcome(
                salesCount = export.salesCount,
                cullCount = export.cullCount,
                labelCount = export.labelCount,
                advanceExportTimestamp = export.advanceTimestamp,
                advancePlantListTimestamp = importOk,
                errorMessage = importErr,
                partialError = export.partialError,
            )
            is ExportStep.Err -> Outcome(
                salesCount = 0,
                cullCount = 0,
                labelCount = 0,
                advanceExportTimestamp = false,
                advancePlantListTimestamp = importOk,
                errorMessage = export.message,
                partialError = null,
            )
        }
    }
}
