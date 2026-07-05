package com.nursery.core

/**
 * Filtering for the Plant List screen: one search box matched across every field.
 *
 * The (trimmed, case-insensitive) query is split on whitespace into words. A plant matches when
 * every word is a substring of at least one field — [Plant.accession], [Plant.name], or
 * [Plant.group]. Words may match different fields (e.g. name + group). ([Plant.light] is no longer
 * a user-facing field and is not searched.) A blank query returns the list unchanged. Input order
 * is preserved.
 */
object PlantSearch {

    fun filter(plants: List<Plant>, query: String): List<Plant> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return plants
        return plants.filter { plant ->
            val fields = listOfNotNull(
                plant.accession.lowercase(),
                plant.name.lowercase(),
                plant.group?.lowercase(),
            )
            words.all { word -> fields.any { field -> field.contains(word) } }
        }
    }
}
