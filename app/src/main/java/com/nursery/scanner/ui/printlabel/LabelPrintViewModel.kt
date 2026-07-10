package com.nursery.scanner.ui.printlabel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nursery.core.LabelPrintRequest
import com.nursery.core.LabelPrintStatus
import com.nursery.core.NurseryStock
import com.nursery.core.Plant
import com.nursery.core.PlantBook
import com.nursery.core.PlantStock
import com.nursery.scanner.data.repo.LabelPrintRepository
import com.nursery.scanner.data.repo.PlantRepository
import com.nursery.scanner.data.settings.SettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LabelPrintDraft(
    val accession: String,
    val name: String,
    val group: String?,
    val stockSummary: String,
    val stockTotal: Int,
    val copies: Int = 1,
) {
    companion object {
        fun fromPlant(p: Plant) = LabelPrintDraft(
            accession = p.accession,
            name = p.name,
            group = p.group,
            stockSummary = PlantStock.summary(p),
            stockTotal = NurseryStock.total(p),
            copies = 1,
        )
    }
}

data class LabelPrintUiState(
    val draft: LabelPrintDraft? = null,
    val notFoundCode: String? = null,
    val notFoundMessage: String? = null,
    val submitError: String? = null,
    val saved: LabelPrintRequest? = null,
)

class LabelPrintViewModel(
    private val plantRepo: PlantRepository,
    private val labelPrintRepo: LabelPrintRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private var book: PlantBook = PlantBook(emptyList())

    private val _ui = MutableStateFlow(LabelPrintUiState())
    val ui: StateFlow<LabelPrintUiState> = _ui.asStateFlow()

    private val _resolved = Channel<Unit>(Channel.CONFLATED)
    val resolved: Flow<Unit> = _resolved.receiveAsFlow()

    private var isSaving = false

    init {
        viewModelScope.launch { plantRepo.plantBook.collect { book = it } }
    }

    fun onCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        val plant = book.findByScan(trimmed)
        if (plant != null) {
            _ui.update {
                it.copy(
                    draft = LabelPrintDraft.fromPlant(plant),
                    notFoundCode = null,
                    notFoundMessage = null,
                    submitError = null,
                )
            }
            _resolved.trySend(Unit)
        } else {
            _ui.update {
                it.copy(
                    notFoundCode = trimmed,
                    notFoundMessage = LabelPrintRequest.NOT_FOUND_MESSAGE,
                    draft = null,
                )
            }
        }
    }

    fun clearNotFound() = _ui.update {
        it.copy(notFoundCode = null, notFoundMessage = null)
    }

    fun discardDraft() = _ui.update { it.copy(draft = null, submitError = null) }

    fun clearSubmitError() = _ui.update { it.copy(submitError = null) }

    fun confirmPrint(copies: Int) {
        val d = _ui.value.draft ?: return
        if (_ui.value.saved != null || isSaving) return

        val request = LabelPrintRequest(
            localId = 0,
            queueId = "",
            createdAtEpochMs = 0,
            status = LabelPrintStatus.PENDING,
            accession = d.accession,
            name = d.name,
            copies = copies,
        )
        LabelPrintRequest.validationError(request)?.let { msg ->
            _ui.update { it.copy(submitError = msg) }
            return
        }
        NurseryStock.copiesCapError(copies, d.stockTotal)?.let { msg ->
            _ui.update { it.copy(submitError = msg) }
            return
        }

        isSaving = true
        viewModelScope.launch {
            try {
                val config = settings.config.first()
                val saved = labelPrintRepo.saveRequest(
                    accession = d.accession,
                    name = d.name,
                    copies = copies,
                    config = config,
                )
                _ui.update { it.copy(saved = saved, submitError = null) }
            } finally {
                isSaving = false
            }
        }
    }

    fun reset() {
        isSaving = false
        _ui.value = LabelPrintUiState()
    }
}
