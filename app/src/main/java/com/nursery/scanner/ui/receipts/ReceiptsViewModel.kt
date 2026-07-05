package com.nursery.scanner.ui.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nursery.core.ReceiptList
import com.nursery.core.ReceiptListItem
import com.nursery.scanner.data.repo.ReceiptRepository
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ReceiptsViewModel(private val repo: ReceiptRepository) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    // Grouped by the "waterline", then interleaved with per-day total footers.
    val items: StateFlow<List<ReceiptListItem>> =
        repo.receipts.map { receipts ->
            ReceiptList.withDayTotals(ReceiptList.grouped(receipts), zone)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun load(id: Long) = repo.receiptById(id)
}
