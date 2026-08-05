package com.nursery.scanner.ui.plants

import com.nursery.core.Plant
import com.nursery.scanner.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlantListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val banksia = Plant(
        accession = "2021-0345",
        name = "Banksia",
        group = "Proteaceae",
        light = "Full sun",
    )
    private val wattle = Plant(
        accession = "2022-0100",
        name = "Wattle",
        group = "Fabaceae",
        light = "Part shade",
    )

    @Test
    fun applyScannedAccession_setsQueryAndFiltersToMatch() = runTest {
        val vm = PlantListViewModel(MutableStateFlow(listOf(banksia, wattle)))
        val collected = mutableListOf<List<Plant>>()
        val job = launch { vm.plants.collect { collected.add(it) } }
        runCurrent()

        vm.applyScannedAccession("2022-0100")
        runCurrent()

        assertEquals("2022-0100", vm.query.value)
        assertEquals(listOf(wattle), collected.last())
        job.cancel()
    }

    @Test
    fun applyScannedAccession_unknownAccession_setsQueryAndEmptyList() = runTest {
        val vm = PlantListViewModel(MutableStateFlow(listOf(banksia, wattle)))
        val collected = mutableListOf<List<Plant>>()
        val job = launch { vm.plants.collect { collected.add(it) } }
        runCurrent()

        vm.applyScannedAccession("9999-0000")
        runCurrent()

        assertEquals("9999-0000", vm.query.value)
        assertEquals(emptyList<Plant>(), collected.last())
        job.cancel()
    }
}
