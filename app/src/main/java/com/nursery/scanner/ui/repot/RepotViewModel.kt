package com.nursery.scanner.ui.repot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nursery.core.Plant
import com.nursery.core.PlantBook
import com.nursery.core.PlantStock
import com.nursery.core.ReadyForSaleFlags
import com.nursery.core.RepotReadyForSale
import com.nursery.core.RepotRecord
import com.nursery.core.RepotStatus
import com.nursery.scanner.data.repo.PlantRepository
import com.nursery.scanner.data.repo.RepotRepository
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

data class RepotDraft(
    val accession: String,
    val name: String,
    val genus: String,
    val species: String,
    val cultivar: String,
    val commonName: String,
    val group: String?,
    val tubesBefore: Int,
    val potsBefore: Int,
    val miscBefore: Int,
    val stockBefore: Int,
    val stockSummary: String,
    val initialForSale: ReadyForSaleFlags,
) {
    companion object {
        fun fromPlant(p: Plant): RepotDraft {
            val initialForSale = RepotReadyForSale.defaults(
                tubes = p.tubesInNursery,
                pots = p.potsInNursery,
                misc = p.miscInNursery,
                group = p.group,
                genus = p.genus,
                sheetPotsForSale = p.potsForSale,
            )
            return RepotDraft(
                accession = p.accession,
                name = p.name,
                genus = p.genus,
                species = p.species,
                cultivar = p.cultivar,
                commonName = p.commonName,
                group = p.group,
                tubesBefore = p.tubesInNursery,
                potsBefore = p.potsInNursery,
                miscBefore = p.miscInNursery,
                stockBefore = p.stockInNursery,
                stockSummary = PlantStock.summary(p),
                initialForSale = initialForSale,
            )
        }
    }
}

data class RepotUiState(
    val draft: RepotDraft? = null,
    val notFoundCode: String? = null,
    val submitError: String? = null,
    val needsAllZeroConfirm: Boolean = false,
    val saved: RepotRecord? = null,
)

class RepotViewModel(
    private val plantRepo: PlantRepository,
    private val repotRepo: RepotRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private var book: PlantBook = PlantBook(emptyList())

    private val _ui = MutableStateFlow(RepotUiState())
    val ui: StateFlow<RepotUiState> = _ui.asStateFlow()

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
                    draft = RepotDraft.fromPlant(plant),
                    notFoundCode = null,
                    submitError = null,
                )
            }
            _resolved.trySend(Unit)
        } else {
            _ui.update {
                it.copy(
                    notFoundCode = trimmed,
                    draft = null,
                )
            }
        }
    }

    fun clearNotFound() = _ui.update { it.copy(notFoundCode = null) }

    fun discardDraft() = _ui.update { it.copy(draft = null, submitError = null, needsAllZeroConfirm = false) }

    fun clearSubmitError() = _ui.update { it.copy(submitError = null) }

    fun clearAllZeroConfirm() = _ui.update { it.copy(needsAllZeroConfirm = false) }

    fun saveRepot(
        tubes: Int,
        pots: Int,
        misc: Int,
        stock: Int,
        tubesForSale: Boolean,
        potsForSale: Boolean,
        miscForSale: Boolean,
        confirmedAllZero: Boolean = false,
    ) {
        val d = _ui.value.draft ?: return
        if (_ui.value.saved != null || isSaving) return

        val candidate = RepotRecord(
            localId = 0,
            repotNo = "",
            createdAtEpochMs = 0,
            status = RepotStatus.PENDING,
            accession = d.accession,
            name = d.name,
            genus = d.genus,
            species = d.species,
            cultivar = d.cultivar,
            commonName = d.commonName,
            group = d.group,
            tubesBefore = d.tubesBefore,
            potsBefore = d.potsBefore,
            miscBefore = d.miscBefore,
            stockBefore = d.stockBefore,
            tubes = tubes,
            pots = pots,
            misc = misc,
            stock = stock,
            tubesForSale = tubesForSale,
            potsForSale = potsForSale,
            miscForSale = miscForSale,
        )
        RepotRecord.validationError(candidate, d.initialForSale)?.let { msg ->
            _ui.update { it.copy(submitError = msg, needsAllZeroConfirm = false) }
            return
        }
        if (candidate.isAllZeroCounts() && !confirmedAllZero) {
            _ui.update { it.copy(needsAllZeroConfirm = true, submitError = null) }
            return
        }

        isSaving = true
        viewModelScope.launch {
            try {
                val config = settings.config.first()
                val saved = repotRepo.saveRepot(
                    accession = d.accession,
                    name = d.name,
                    genus = d.genus,
                    species = d.species,
                    cultivar = d.cultivar,
                    commonName = d.commonName,
                    group = d.group,
                    tubesBefore = d.tubesBefore,
                    potsBefore = d.potsBefore,
                    miscBefore = d.miscBefore,
                    stockBefore = d.stockBefore,
                    tubes = tubes,
                    pots = pots,
                    misc = misc,
                    stock = stock,
                    tubesForSale = tubesForSale,
                    potsForSale = potsForSale,
                    miscForSale = miscForSale,
                    config = config,
                )
                _ui.update { it.copy(saved = saved, submitError = null, needsAllZeroConfirm = false) }
            } finally {
                isSaving = false
            }
        }
    }

    fun reset() {
        isSaving = false
        _ui.value = RepotUiState()
    }
}
