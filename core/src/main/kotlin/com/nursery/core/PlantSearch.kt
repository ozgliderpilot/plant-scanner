package com.nursery.core

/**
 * Filtering for the Plant List screen: one search box matched across every field.
 *
 * A plant matches when the (trimmed, case-insensitive) query is a substring of any of its
 * fields — [Plant.accession], [Plant.name], [Plant.group], or [Plant.light]. A blank query
 * returns the list unchanged. Input order is preserved.
 */
object PlantSearch {

    fun filter(plants: List<Plant>, query: String): List<Plant> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return plants
        return plants.filter { plant ->
            plant.accession.lowercase().contains(q) ||
                plant.name.lowercase().contains(q) ||
                plant.group?.lowercase()?.contains(q) == true ||
                plant.light?.lowercase()?.contains(q) == true
        }
    }
}
