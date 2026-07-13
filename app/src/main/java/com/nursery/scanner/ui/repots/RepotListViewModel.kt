package com.nursery.scanner.ui.repots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nursery.core.RepotRecord
import com.nursery.core.RepotSearch
import com.nursery.scanner.data.repo.RepotRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class RepotListViewModel(repotRepository: RepotRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val repots: StateFlow<List<RepotRecord>> =
        combine(repotRepository.repots, _query) { list, q -> RepotSearch.filter(list, q) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }
}
