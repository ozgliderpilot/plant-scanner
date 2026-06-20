package com.nursery.scanner.ui.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nursery.core.Receipt
import com.nursery.core.ReceiptList
import com.nursery.scanner.data.repo.ReceiptRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ReceiptsViewModel(private val repo: ReceiptRepository) : ViewModel() {

    // Grouped by the "waterline": pending (not-yet-exported) receipts first, then exported.
    val receipts: StateFlow<List<Receipt>> =
        repo.receipts.map(ReceiptList::grouped)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun load(id: Long): Receipt? = repo.receiptById(id)
}
