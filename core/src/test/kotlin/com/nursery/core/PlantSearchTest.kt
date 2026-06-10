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

    @Test fun `matches on light`() {
        assertEquals(listOf("2021-0345"), accessions(PlantSearch.filter(plants, "Full sun")))
    }

    @Test fun `matching is case-insensitive`() {
        assertEquals(listOf("2021-0345"), accessions(PlantSearch.filter(plants, "banksia")))
    }

    @Test fun `matches a substring, not just a prefix`() {
        // "shade" appears mid-string in "Part shade"
        assertEquals(listOf("2022-0100"), accessions(PlantSearch.filter(plants, "shade")))
    }

    @Test fun `query is trimmed before matching`() {
        assertEquals(listOf("2021-0345"), accessions(PlantSearch.filter(plants, "  Banksia  ")))
    }

    @Test fun `null group and light do not crash and do not match`() {
        // Grevillea has null group/light; searching a term only those could hold finds nothing.
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
}
