package com.lbc.breadcrumb.embed

/**
 * A search phrase taken apart ([PhraseRules]): the words left to search, the
 * kinds it asks for, its days (YYYY-MM-DD, both inclusive), and the app or site
 * it came from. The shape the server's POST /parse answered with, before L6.
 */
data class Interpretation(
    val query: String = "",
    val types: List<String> = emptyList(),
    val from: String? = null,
    val to: String? = null,
    val sourceApp: String? = null,
)

/**
 * Where a result placed by meaning (vector) and by words (text).
 * Null for a half that did not rank it.
 */
data class Ranks(
    val vector: Int? = null,
    val text: Int? = null,
)

/** One memory in the ranked answer ([LocalSearch]): its id, its fused score, and its ranks. */
data class SearchHit(
    val id: String,
    val score: Double? = null,
    val ranks: Ranks? = null,
)

/** What the ranking answered. */
sealed interface SearchOutcome {
    data class Found(
        val hits: List<SearchHit>,
        val interpretation: Interpretation?,
    ) : SearchOutcome

    data class Unavailable(
        val reason: String,
        val offline: Boolean,
    ) : SearchOutcome
}
