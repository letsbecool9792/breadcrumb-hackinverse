package com.lbc.breadcrumb.ui.home

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.embed.SearchHit

/**
 * A combined search result pairing the local [Memory] with server [SearchHit] metadata.
 * Hit is null when the result came from local word search before server ranking.
 */
data class SearchResult(
    val memory: Memory,
    val hit: SearchHit? = null,
)

/**
 * Joins server [hits] with memories [held] on the phone.
 * Keeps the server's ranking order and drops any hit the phone does not hold.
 */
fun joinHits(hits: List<SearchHit>, held: List<Memory>): List<SearchResult> {
    if (hits.isEmpty() || held.isEmpty()) return emptyList()
    val heldById = held.associateBy { it.id }
    return hits.mapNotNull { hit ->
        val memory = heldById[hit.id] ?: return@mapNotNull null
        SearchResult(memory = memory, hit = hit)
    }
}
