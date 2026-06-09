package com.nursery.scanner.ui.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nursery.core.Receipt
import com.nursery.scanner.data.repo.ReceiptRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ReceiptsViewModel(private val repo: ReceiptRepository) : ViewModel() {

    val receipts: StateFlow<List<Receipt>> =
        repo.receipts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun load(id: Long): Receipt? = repo.receiptById(id)
}
