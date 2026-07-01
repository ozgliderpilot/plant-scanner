package com.nursery.scanner.ui.cull

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nursery.core.CullReason
import com.nursery.core.CullRecord
import com.nursery.core.CullUnit
import com.nursery.core.Plant
import com.nursery.core.PlantBook
import com.nursery.core.SaleUnit
import com.nursery.scanner.data.repo.CullRepository
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

data class CullDraft(
    val accession: String,
    val name: String,
    val group: String?,
    val isUnknown: Boolean,
    val qty: Int,
    val unit: SaleUnit,
    val reason: CullReason,
    val notes: String,
    val tubesInNursery: Int,
    val potsInNursery: Int,
    val miscInNursery: Int,
) {
    companion object {
        fun fromPlant(p: Plant) = CullDraft(
            accession = p.accession,
            name = p.name,
            group = p.group,
            isUnknown = false,
            qty = 1,
            unit = CullUnit.defaultFor(p.tubesInNursery, p.potsInNursery, p.miscInNursery),
            reason = CullReason.DEFAULT,
            notes = "",
            tubesInNursery = p.tubesInNursery,
            potsInNursery = p.potsInNursery,
            miscInNursery = p.miscInNursery,
        )

        fun unknown(code: String) = CullDraft(
            accession = code,
            name = PlantBook.UNKNOWN_NAME,
            group = null,
            isUnknown = true,
            qty = 1,
            unit = CullUnit.defaultFor(0, 0, 0),
            reason = CullReason.DEFAULT,
            notes = "",
            tubesInNursery = 0,
            potsInNursery = 0,
            miscInNursery = 0,
        )
    }
}

data class CullUiState(
    val draft: CullDraft? = null,
    val notFoundCode: String? = null,
    val saved: CullRecord? = null,
)

class CullViewModel(
    private val plantRepo: PlantRepository,
    private val cullRepo: CullRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private var book: PlantBook = PlantBook(emptyList())

    private val _ui = MutableStateFlow(CullUiState())
    val ui: StateFlow<CullUiState> = _ui.asStateFlow()

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
            _ui.update { it.copy(draft = CullDraft.fromPlant(plant), notFoundCode = null) }
            _resolved.trySend(Unit)
        } else {
            _ui.update { it.copy(notFoundCode = trimmed) }
        }
    }

    fun cullAsUnknown() {
        val code = _ui.value.notFoundCode ?: return
        _ui.update { it.copy(draft = CullDraft.unknown(code), notFoundCode = null) }
        _resolved.trySend(Unit)
    }

    fun clearNotFound() = _ui.update { it.copy(notFoundCode = null) }

    fun discardDraft() = _ui.update { it.copy(draft = null) }

    fun recordCull(qty: Int, unit: SaleUnit, reason: CullReason, notes: String) {
        val d = _ui.value.draft ?: return
        if (_ui.value.saved != null || isSaving) return
        val record = CullRecord(
            localId = 0,
            cullNo = "",
            createdAtEpochMs = 0,
            status = com.nursery.core.CullStatus.PENDING,
            accession = d.accession,
            name = d.name,
            group = d.group,
            isUnknown = d.isUnknown,
            qty = qty,
            unit = unit,
            reason = reason,
            notes = notes.takeIf { it.isNotBlank() },
        )
        CullRecord.validationError(record)?.let { return }
        isSaving = true
        viewModelScope.launch {
            try {
                val config = settings.config.first()
                val saved = cullRepo.saveCull(
                    accession = d.accession,
                    name = d.name,
                    group = d.group,
                    isUnknown = d.isUnknown,
                    qty = qty,
                    unit = unit,
                    reason = reason,
                    notes = notes.takeIf { it.isNotBlank() },
                    config = config,
                )
                _ui.update { it.copy(saved = saved, draft = null) }
            } finally {
                isSaving = false
            }
        }
    }

    fun reset() {
        isSaving = false
        _ui.value = CullUiState()
    }
}
