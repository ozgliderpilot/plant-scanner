package com.nursery.scanner.ui.plants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nursery.core.Plant
import com.nursery.core.PlantSearch
import com.nursery.scanner.data.repo.PlantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Backs the read-only Plant List screen: the cached plants filtered live by a single search box
 * (matched across all fields via [PlantSearch]). A blank query yields the whole cache, so an empty
 * result with a blank query means the cache itself is empty (the screen uses that to pick its
 * empty-state message).
 */
class PlantListViewModel(plantRepository: PlantRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val plants: StateFlow<List<Plant>> =
        combine(plantRepository.plants, _query) { list, q -> PlantSearch.filter(list, q) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }
}
