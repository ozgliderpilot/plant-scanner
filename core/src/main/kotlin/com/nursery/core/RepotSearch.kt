package com.nursery.core

/**
 * Filtering for the View Repots screen: one search box matched across accession, name,
 * genus, group, and repot id. A blank query returns the list unchanged.
 */
object RepotSearch {

    fun filter(repots: List<RepotRecord>, query: String): List<RepotRecord> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return repots
        return repots.filter { repot ->
            repot.accession.lowercase().contains(q) ||
                repot.name.lowercase().contains(q) ||
                repot.genus.lowercase().contains(q) ||
                repot.group?.lowercase()?.contains(q) == true ||
                repot.repotNo.lowercase().contains(q)
        }
    }
}
