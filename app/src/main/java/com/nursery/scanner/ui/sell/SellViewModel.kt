package com.nursery.scanner.ui.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nursery.core.LineItem
import com.nursery.core.Money
import com.nursery.core.Plant
import com.nursery.core.PlantBook
import com.nursery.core.Receipt
import com.nursery.scanner.data.repo.PlantRepository
import com.nursery.scanner.data.repo.ReceiptRepository
import com.nursery.scanner.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val pots: Int,
    val unitPriceCents: Long,
    val discountPct: Int,
    val editIndex: Int?,
) {
    companion object {
        fun fromPlant(p: Plant) = LineDraft(
            accession = p.accession, name = p.name, group = p.group, light = p.light,
            isUnknown = false, pots = 1, unitPriceCents = 0, discountPct = 0, editIndex = null,
        )

        fun unknown(code: String) = LineDraft(
            accession = code, name = PlantBook.UNKNOWN_NAME, group = null, light = null,
            isUnknown = true, pots = 1, unitPriceCents = 0, discountPct = 0, editIndex = null,
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
        } else {
            _ui.update { it.copy(notFoundCode = trimmed) }
        }
    }

    /** Not-found edge path: sell the scanned code as "unknown" (decision #7). */
    fun sellAsUnknown() {
        val code = _ui.value.notFoundCode ?: return
        _ui.update { it.copy(draft = LineDraft.unknown(code), notFoundCode = null) }
    }

    fun clearNotFound() = _ui.update { it.copy(notFoundCode = null) }

    fun discardDraft() = _ui.update { it.copy(draft = null) }

    /** ② Commit the line-item form (add new, or replace when editing). */
    fun commitDraft(pots: Int, unitPriceCents: Long, discountPct: Int) {
        val d = _ui.value.draft ?: return
        val line = LineItem(
            accession = d.accession,
            name = d.name,
            pots = pots,
            unitPriceCents = unitPriceCents,
            discountPct = discountPct,
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
                    pots = line.pots,
                    unitPriceCents = line.unitPriceCents,
                    discountPct = line.discountPct,
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
        if (_ui.value.lines.isEmpty()) return
        viewModelScope.launch {
            val config = settings.config.first()
            val receipt = receiptRepo.saveReceipt(_ui.value.lines, config)
            _ui.update { it.copy(saved = receipt) }
        }
    }

    /** Start a fresh receipt (after Done / New sale). */
    fun reset() {
        _ui.value = SellUiState()
    }
}
