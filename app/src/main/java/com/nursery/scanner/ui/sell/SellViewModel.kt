package com.nursery.scanner.ui.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nursery.core.LineItem
import com.nursery.core.Money
import com.nursery.core.Plant
import com.nursery.core.PlantBook
import com.nursery.core.Receipt
import com.nursery.core.SaleUnit
import com.nursery.scanner.data.repo.PlantRepository
import com.nursery.scanner.data.repo.ReceiptRepository
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

/**
 * The plant being turned into a line, plus the editable values (and edit slot, if editing).
 * [accession] is the scanned/typed code (== barcode); for an unknown line it is that same code.
 */
data class LineDraft(
    val accession: String,
    val name: String,
    val group: String?,
    val light: String?,
    val isUnknown: Boolean,
    val qty: Int,
    val unitPriceCents: Long,
    val discountPct: Int,
    val unit: SaleUnit,
    val potsInNursery: Int,
    val tubesInNursery: Int,
    val miscInNursery: Int,
    val editIndex: Int?,
) {
    companion object {
        fun fromPlant(p: Plant) = LineDraft(
            accession = p.accession, name = p.name, group = p.group, light = p.light,
            isUnknown = false, qty = 1, unitPriceCents = 0, discountPct = 0,
            unit = SaleUnit.defaultFor(p.potsInNursery, p.tubesInNursery, p.miscInNursery),
            potsInNursery = p.potsInNursery, tubesInNursery = p.tubesInNursery, miscInNursery = p.miscInNursery,
            editIndex = null,
        )

        fun unknown(code: String) = LineDraft(
            accession = code, name = PlantBook.UNKNOWN_NAME, group = null, light = null,
            isUnknown = true, qty = 1, unitPriceCents = 0, discountPct = 0,
            unit = SaleUnit.POTS, potsInNursery = 0, tubesInNursery = 0, miscInNursery = 0,
            editIndex = null,
        )
    }
}

data class SellUiState(
    val lines: List<LineItem> = emptyList(),
    val draft: LineDraft? = null,
    val notFoundCode: String? = null,
    val saved: Receipt? = null,
) {
    val totalCents: Long get() = Money.receiptTotalCents(lines)
    val isEmpty: Boolean get() = lines.isEmpty()
}

/**
 * Drives the whole Sell flow (spec ①–④). One instance is shared across the sell nav graph, so the
 * receipt-in-progress survives Scan -> LineItem -> Cart -> Confirm.
 */
class SellViewModel(
    private val plantRepo: PlantRepository,
    private val receiptRepo: ReceiptRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private var book: PlantBook = PlantBook(emptyList())

    private val _ui = MutableStateFlow(SellUiState())
    val ui: StateFlow<SellUiState> = _ui.asStateFlow()

    // One-shot "a scan resolved into a draft" events. Navigation is driven by these, NOT by the
    // persistent `draft` state, so returning to the Scan screen with a left-over draft (e.g. after
    // editing a cart line) can't bounce the user forward into the line-item form again.
    private val _resolved = Channel<Unit>(Channel.CONFLATED)
    val resolved: Flow<Unit> = _resolved.receiveAsFlow()

    // Guards against a double-tap on "Finish & save" creating two receipts for one sale.
    private var isSaving = false

    init {
        viewModelScope.launch { plantRepo.plantBook.collect { book = it } }
    }

    /** ① A scanned or typed code: resolve to a found plant (-> draft) or a not-found state. */
    fun onCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        val plant = book.findByScan(trimmed)
        if (plant != null) {
            _ui.update { it.copy(draft = LineDraft.fromPlant(plant), notFoundCode = null) }
            _resolved.trySend(Unit)
        } else {
            _ui.update { it.copy(notFoundCode = trimmed) }
        }
    }

    /** Not-found edge path: sell the scanned code as "unknown" (decision #7). */
    fun sellAsUnknown() {
        val code = _ui.value.notFoundCode ?: return
        _ui.update { it.copy(draft = LineDraft.unknown(code), notFoundCode = null) }
        _resolved.trySend(Unit)
    }

    fun clearNotFound() = _ui.update { it.copy(notFoundCode = null) }

    fun discardDraft() = _ui.update { it.copy(draft = null) }

    /** ② Commit the line-item form (add new, or replace when editing). */
    fun commitDraft(qty: Int, unitPriceCents: Long, discountPct: Int, unit: SaleUnit) {
        val d = _ui.value.draft ?: return
        val line = LineItem(
            accession = d.accession,
            name = d.name,
            qty = qty,
            unitPriceCents = unitPriceCents,
            discountPct = discountPct,
            unit = unit,
        )
        val lines = _ui.value.lines.toMutableList()
        if (d.editIndex != null && d.editIndex in lines.indices) lines[d.editIndex] = line else lines.add(line)
        _ui.update { it.copy(lines = lines, draft = null, notFoundCode = null) }
    }

    /** ③ Tap a line to edit it (re-enrich group/light from the book if still cached). */
    fun beginEdit(index: Int) {
        val line = _ui.value.lines.getOrNull(index) ?: return
        val plant = book.findByScan(line.accession)
        _ui.update {
            it.copy(
                draft = LineDraft(
                    accession = line.accession,
                    name = line.name,
                    group = plant?.group,
                    light = plant?.light,
                    isUnknown = PlantBook.isUnknown(line),
                    qty = line.qty,
                    unitPriceCents = line.unitPriceCents,
                    discountPct = line.discountPct,
                    unit = line.unit,
                    potsInNursery = plant?.potsInNursery ?: 0,
                    tubesInNursery = plant?.tubesInNursery ?: 0,
                    miscInNursery = plant?.miscInNursery ?: 0,
                    editIndex = index,
                ),
            )
        }
    }

    fun removeLine(index: Int) {
        val lines = _ui.value.lines.toMutableList()
        if (index in lines.indices) lines.removeAt(index)
        _ui.update { it.copy(lines = lines) }
    }

    /** ③ -> ④ Save the receipt locally as SAVED (pending export). */
    fun finishAndSave() {
        if (_ui.value.lines.isEmpty() || _ui.value.saved != null || isSaving) return
        isSaving = true
        viewModelScope.launch {
            try {
                val config = settings.config.first()
                val receipt = receiptRepo.saveReceipt(_ui.value.lines, config)
                _ui.update { it.copy(saved = receipt) }
            } finally {
                isSaving = false
            }
        }
    }

    /** Start a fresh receipt (after Done / New sale). */
    fun reset() {
        isSaving = false
        _ui.value = SellUiState()
    }
}
