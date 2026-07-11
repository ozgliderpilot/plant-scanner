package com.nursery.scanner.ui.labels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nursery.core.LabelPrintRequest
import com.nursery.core.LabelPrintSearch
import com.nursery.scanner.data.repo.LabelPrintRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class LabelListViewModel(labelPrintRepository: LabelPrintRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val requests: StateFlow<List<LabelPrintRequest>> =
        combine(labelPrintRepository.requests, _query) { list, q -> LabelPrintSearch.filter(list, q) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }
}
