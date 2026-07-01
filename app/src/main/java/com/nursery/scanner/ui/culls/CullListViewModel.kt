package com.nursery.scanner.ui.culls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nursery.core.CullRecord
import com.nursery.core.CullSearch
import com.nursery.scanner.data.repo.CullRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class CullListViewModel(cullRepository: CullRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val culls: StateFlow<List<CullRecord>> =
        combine(cullRepository.culls, _query) { list, q -> CullSearch.filter(list, q) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }
}
