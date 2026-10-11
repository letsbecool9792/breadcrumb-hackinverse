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
