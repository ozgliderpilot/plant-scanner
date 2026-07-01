package com.nursery.core

/**
 * Filtering for the View culled plants screen: one search box matched across accession, name,
 * group, reason label, and notes. A blank query returns the list unchanged.
 */
object CullSearch {

    fun filter(culls: List<CullRecord>, query: String): List<CullRecord> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return culls
        return culls.filter { cull ->
            cull.accession.lowercase().contains(q) ||
                cull.name.lowercase().contains(q) ||
                cull.group?.lowercase()?.contains(q) == true ||
                cull.reason.label.lowercase().contains(q) ||
                cull.notes?.lowercase()?.contains(q) == true
        }
    }
}
