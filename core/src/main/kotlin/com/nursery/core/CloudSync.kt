package com.nursery.core

/**
 * Combines the two legs of a **cloud sync**: export the sync queue, then import the plant list.
 *
 * Per-step successes still advance their own timestamps even when the overall result is failure.
 * When both legs fail, the export error wins (queue/money first).
 */
object CloudSync {

    sealed interface Step {
        data class Ok(
            val salesCount: Int = 0,
            val cullCount: Int = 0,
            val plantCount: Int = 0,
            val partialError: String? = null,
        ) : Step

        data class Err(val message: String) : Step
    }

    data class Outcome(
        val salesCount: Int,
        val cullCount: Int,
        val plantCount: Int,
        val advanceExportTimestamp: Boolean,
        val advancePlantListTimestamp: Boolean,
        val errorMessage: String?,
        val partialError: String?,
    )

    fun combine(export: Step, import: Step): Outcome {
        val exportOk = export as? Step.Ok
        val importOk = import as? Step.Ok
        val exportErr = (export as? Step.Err)?.message
        val importErr = (import as? Step.Err)?.message

        return Outcome(
            salesCount = exportOk?.salesCount ?: 0,
            cullCount = exportOk?.cullCount ?: 0,
            plantCount = importOk?.plantCount ?: 0,
            advanceExportTimestamp = exportOk != null,
            advancePlantListTimestamp = importOk != null,
            errorMessage = when {
                exportErr != null -> exportErr
                importErr != null -> importErr
                else -> null
            },
            partialError = exportOk?.partialError,
        )
    }
}
