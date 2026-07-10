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
            val partialError: String? = null,
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
        val advanceExportTimestamp: Boolean,
        val advancePlantListTimestamp: Boolean,
        val errorMessage: String?,
        val partialError: String?,
    )

    fun combine(export: ExportStep, import: ImportStep): Outcome {
        val exportOk = when (export) {
            is ExportStep.Ok -> export
            is ExportStep.Err -> null
        }
        val exportErr = when (export) {
            is ExportStep.Ok -> null
            is ExportStep.Err -> export.message
        }
        val importOk = import is ImportStep.Ok
        val importErr = when (import) {
            ImportStep.Ok -> null
            is ImportStep.Err -> import.message
        }

        return Outcome(
            salesCount = exportOk?.salesCount ?: 0,
            cullCount = exportOk?.cullCount ?: 0,
            advanceExportTimestamp = exportOk != null,
            advancePlantListTimestamp = importOk,
            errorMessage = exportErr ?: importErr,
            partialError = exportOk?.partialError,
        )
    }
}
