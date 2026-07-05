package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PlantSearchTest {

    private val plants = listOf(
        Plant(accession = "2021-0345", name = "Banksia", group = "Proteaceae", light = "Full sun"),
        Plant(accession = "2022-0100", name = "Wattle", group = "Fabaceae", light = "Part shade"),
        Plant(accession = "2023-0500", name = "Grevillea", group = null, light = null),
    )

    private fun accessions(result: List<Plant>) = result.map { it.accession }

    @Test fun `blank query returns the full list unchanged`() {
        assertEquals(plants, PlantSearch.filter(plants, ""))
    }

    @Test fun `whitespace-only query returns the full list unchanged`() {
        assertEquals(plants, PlantSearch.filter(plants, "   "))
    }

    @Test fun `matches on accession`() {
        assertEquals(listOf("2022-0100"), accessions(PlantSearch.filter(plants, "2022-0100")))
    }

    @Test fun `matches on name`() {
        assertEquals(listOf("2021-0345"), accessions(PlantSearch.filter(plants, "Banksia")))
    }

    @Test fun `matches on group`() {
        assertEquals(listOf("2022-0100"), accessions(PlantSearch.filter(plants, "Fabaceae")))
    }

    @Test fun `does not match on light`() {
        // light is no longer a searchable field (issue #3); searching a light value finds nothing.
        assertEquals(emptyList(), accessions(PlantSearch.filter(plants, "Full sun")))
    }

    @Test fun `matching is case-insensitive`() {
        assertEquals(listOf("2021-0345"), accessions(PlantSearch.filter(plants, "banksia")))
    }

    @Test fun `matches a substring, not just a prefix`() {
        // "rotea" appears mid-string in the group "Proteaceae"
        assertEquals(listOf("2021-0345"), accessions(PlantSearch.filter(plants, "rotea")))
    }

    @Test fun `query is trimmed before matching`() {
        assertEquals(listOf("2021-0345"), accessions(PlantSearch.filter(plants, "  Banksia  ")))
    }

    @Test fun `null group does not crash and does not match`() {
        // Grevillea has a null group; searching a term only the group could hold finds nothing.
        assertEquals(emptyList(), accessions(PlantSearch.filter(plants, "Proteaceae").filter { it.accession == "2023-0500" }))
        // And Grevillea itself is still findable by its own fields.
        assertEquals(listOf("2023-0500"), accessions(PlantSearch.filter(plants, "Grevillea")))
    }

    @Test fun `a query matching nothing returns empty`() {
        assertEquals(emptyList(), PlantSearch.filter(plants, "no-such-plant"))
    }

    @Test fun `preserves input order`() {
        // "0" appears in every accession; result must keep the original order.
        assertEquals(
            listOf("2021-0345", "2022-0100", "2023-0500"),
            accessions(PlantSearch.filter(plants, "0")),
        )
    }

    private val begoniaNellie = Plant(
        accession = "2024-0001",
        name = "Begonia 'Nellie Bly'",
        group = "Begoniaceae",
        light = null,
    )

    private val begoniaErythro = Plant(
        accession = "2024-0002",
        name = "Begonia x erythrophylla",
        group = "Begoniaceae",
        light = null,
    )

    private val begoniaPlants = listOf(begoniaNellie, begoniaErythro)

    @Test fun `matches any single word from a multi-word plant name`() {
        assertEquals(listOf("2024-0001", "2024-0002"), accessions(PlantSearch.filter(begoniaPlants, "begonia")))
        assertEquals(listOf("2024-0001"), accessions(PlantSearch.filter(begoniaPlants, "nellie")))
        assertEquals(listOf("2024-0001"), accessions(PlantSearch.filter(begoniaPlants, "bly")))
    }

    @Test fun `matches any combination of words in the plant name`() {
        assertEquals(listOf("2024-0001"), accessions(PlantSearch.filter(begoniaPlants, "begonia nellie")))
        assertEquals(listOf("2024-0001"), accessions(PlantSearch.filter(begoniaPlants, "begonia bly")))
        assertEquals(listOf("2024-0001"), accessions(PlantSearch.filter(begoniaPlants, "nellie bly")))
        assertEquals(listOf("2024-0001"), accessions(PlantSearch.filter(begoniaPlants, "Begonia 'Nellie Bly'")))
    }

    @Test fun `matches partial words and hybrid notation in botanical names`() {
        assertEquals(listOf("2024-0002"), accessions(PlantSearch.filter(begoniaPlants, "erythro")))
        assertEquals(listOf("2024-0002"), accessions(PlantSearch.filter(begoniaPlants, "begonia erythro")))
        assertEquals(listOf("2024-0002"), accessions(PlantSearch.filter(begoniaPlants, "begonia x erythro")))
        assertEquals(listOf("2024-0002"), accessions(PlantSearch.filter(begoniaPlants, "erythrophylla")))
        assertEquals(listOf("2024-0002"), accessions(PlantSearch.filter(begoniaPlants, "Begonia x erythrophylla")))
    }

    @Test fun `multi-word query can match words across name and group fields`() {
        val plant = Plant(accession = "2024-0003", name = "Banksia", group = "Proteaceae", light = null)
        assertEquals(listOf("2024-0003"), accessions(PlantSearch.filter(listOf(plant), "banksia proteaceae")))
    }
}
