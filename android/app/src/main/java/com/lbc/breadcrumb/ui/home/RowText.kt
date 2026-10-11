package com.lbc.breadcrumb.ui.home

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.net.SearchHit

/**
 * Why a result matched, in one place (design §9): the words around the hit, with
 * the hit to be lit, or for a match by meaning alone, what the model made of it.
 */
internal sealed interface Why {
    data class Fragment(val lead: String, val hit: String, val tail: String) : Why
    data class Summary(val text: String) : Why
}

/** What a result row says: its title, and why it matched when there is something to say. */
internal data class RowText(val title: String, val why: Why?)

/**
 * The text of a result row. The summary and the text the model read come from
 * the hit when the server ranked it, else from the phone's own row; [words] are
 * what to light.
 */
internal fun rowText(memory: Memory, hit: SearchHit?, words: List<String>): RowText {
    val summary = hit?.summary ?: memory.summary
    val readText = hit?.readText ?: memory.readText
    val title = ResultText.title(memory, summary)

    val fragment = ResultText.fragment(
        texts = listOf(memory.rawText, memory.note, memory.extractedText, readText, memory.title, summary),
        words = words,
    )
    val why = when {
        fragment != null -> Why.Fragment(fragment.lead, fragment.hit, fragment.tail)
        !summary.isNullOrBlank() && summary != title -> Why.Summary(summary)
        else -> null
    }
    return RowText(title, why)
}

/** The words to light: what the server took the phrase to mean, else the phrase itself. */
internal fun wordsToLight(phrase: String, interpretedQuery: String?): List<String> =
    ResultText.words(interpretedQuery?.takeIf { it.isNotBlank() } ?: phrase)
