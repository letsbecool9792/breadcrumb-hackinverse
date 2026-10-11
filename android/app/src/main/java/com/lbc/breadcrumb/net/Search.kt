package com.lbc.breadcrumb.net

import kotlinx.serialization.Serializable

/**
 * The server's interpretation of a search phrase, decomposing it into
 * words, type filters, and date/source boundaries.
 * Matching docs/api.md.
 */
@Serializable
data class Interpretation(
    val query: String = "",
    val types: List<String> = emptyList(),
    val from: String? = null,
    val to: String? = null,
    val sourceApp: String? = null,
)

@Serializable
internal data class ParseRequest(
    val phrase: String,
    val sources: List<String>,
)

@Serializable
internal data class ParseReply(
    val interpretation: Interpretation,
)

/**
 * Where a result placed by meaning (vector) and by words (text).
 * Null for a half that did not rank it.
 */
@Serializable
data class Ranks(
    val vector: Int? = null,
    val text: Int? = null,
)

/**
 * A search result from GET /search matching docs/api.md.
 * `ignoreUnknownKeys` is configured on the JSON decoder.
 */
@Serializable
data class SearchHit(
    val id: String,
    val score: Double? = null,
    val ranks: Ranks? = null,
    val type: String? = null,
    val hasLink: Boolean = false,
    val capturedAt: String? = null,
    val datedAt: String? = null,
    val sourceAppLabel: String? = null,
    val title: String? = null,
    val rawText: String? = null,
    val extractedText: String? = null,
    val note: String? = null,
    val readText: String? = null,
    val summary: String? = null,
    val kind: String? = null,
)

@Serializable
internal data class SearchReply(
    val query: String = "",
    val interpretation: Interpretation? = null,
    val interpretationError: String? = null,
    val results: List<SearchHit> = emptyList(),
)

/**
 * Outcome of a GET /search call.
 */
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
