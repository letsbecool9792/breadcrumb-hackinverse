package com.lbc.breadcrumb.ui.home

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.embed.Interpretation
import com.lbc.breadcrumb.embed.SearchOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/**
 * The presentation state of search on the home screen (§9).
 */
sealed interface SearchState {
    /** No search in flight; the screen shows the resting mosaic. */
    data object Resting : SearchState

    /** A search is active or has answered. */
    data class Searching(
        val phrase: String,
        val results: List<SearchResult>,
        val interpretation: Interpretation? = null,
        val status: SearchStatus,
    ) : SearchState
}

/**
 * Normalises a query string: trims leading/trailing whitespace and collapses
 * consecutive whitespace runs into a single space.
 */
fun normaliseQuery(query: String): String =
    query.trim().replace(Regex("\\s+"), " ")

/**
 * Logic for design §9: two answers in turn.
 *
 * Turns a [Flow] of typed query strings into a [Flow] of [SearchState].
 * On every change of the normalised query:
 * 1. Empty -> [SearchState.Resting]
 * 2. Word search answers immediately with [SearchStatus.RANKING] and no interpretation
 * 3. After 450 ms, the ranking answers:
 *    - [SearchOutcome.Found] -> joined hits with the phrase's interpretation and [SearchStatus.RANKED]
 *    - [SearchOutcome.Unavailable] -> local word matches stand, with [SearchStatus.OFFLINE] (if offline)
 *      or [SearchStatus.UNAVAILABLE]
 *
 * Incoming [wordSearch], [rankedSearch], and [heldMemories] are passed as functions so
 * tests can fake or mock them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun twoStageSearch(
    queries: Flow<String>,
    wordSearch: suspend (phrase: String) -> List<Memory>,
    rankedSearch: suspend (phrase: String) -> SearchOutcome,
    heldMemories: suspend () -> List<Memory>,
    rankDelayMillis: Long = 450L,
): Flow<SearchState> {
    return queries
        .map { normaliseQuery(it) }
        .distinctUntilChanged()
        .transformLatest { phrase ->
            if (phrase.isEmpty()) {
                emit(SearchState.Resting)
                return@transformLatest
            }

            // 1. Immediate local word search (RANKING)
            val localMatches = wordSearch(phrase)
            val initialResults = localMatches.map { SearchResult(memory = it, hit = null) }
            emit(
                SearchState.Searching(
                    phrase = phrase,
                    results = initialResults,
                    interpretation = null,
                    status = SearchStatus.RANKING,
                ),
            )

            // 2. Pause before ranking; if a new query arrives during pause,
            // transformLatest cancels this block automatically.
            delay(rankDelayMillis)

            // 3. The ranking answers
            val outcome = rankedSearch(phrase)
            when (outcome) {
                is SearchOutcome.Found -> {
                    val held = heldMemories()
                    val joined = joinHits(outcome.hits, held)
                    emit(
                        SearchState.Searching(
                            phrase = phrase,
                            results = joined,
                            interpretation = outcome.interpretation,
                            status = SearchStatus.RANKED,
                        ),
                    )
                }

                is SearchOutcome.Unavailable -> {
                    val finalStatus = if (outcome.offline) {
                        SearchStatus.OFFLINE
                    } else {
                        SearchStatus.UNAVAILABLE
                    }
                    emit(
                        SearchState.Searching(
                            phrase = phrase,
                            results = initialResults,
                            interpretation = null,
                            status = finalStatus,
                        ),
                    )
                }
            }
        }
}
