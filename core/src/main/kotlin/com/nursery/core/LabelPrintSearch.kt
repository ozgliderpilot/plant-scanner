package com.nursery.core

/**
 * Filtering for the View Labels screen: one search box matched across accession, name,
 * queue id, and copies. A blank query returns the list unchanged.
 */
object LabelPrintSearch {

    fun filter(requests: List<LabelPrintRequest>, query: String): List<LabelPrintRequest> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return requests
        return requests.filter { request ->
            request.accession.lowercase().contains(q) ||
                request.name.lowercase().contains(q) ||
                request.queueId.lowercase().contains(q) ||
                request.copies.toString().contains(q)
        }
    }
}
