package com.lbc.breadcrumb.ui.home

import com.lbc.breadcrumb.data.Memory
import java.util.Locale

/**
 * Pure snippet extraction for search result rows (L5).
 *
 * Rules:
 * - Return the relevant one-line/window around the best match for query words.
 * - Search across: rawText, note, extractedText, title.
 * - Case-insensitive.
 * - Window is about 90 characters around the best match.
 * - Add ellipsis when text is cut.
 * - Return null when no query word matches.
 */
object Snippet {

    fun of(
        memory: Memory,
        words: List<String>,
        targetWindow: Int = 90,
    ): String? {
        val cleanWords = words
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
        if (cleanWords.isEmpty()) return null

        val sortedWords = cleanWords.sortedByDescending { it.length }

        val candidates = listOfNotNull(
            memory.rawText,
            memory.note,
            memory.extractedText,
            memory.title,
        )

        for (text in candidates) {
            val collapsed = text.trim().replace(Regex("""\s+"""), " ")
            if (collapsed.isEmpty()) continue

            var earliestStart = -1
            var bestLength = -1

            for (w in sortedWords) {
                val idx = collapsed.indexOf(w, ignoreCase = true)
                if (idx != -1) {
                    if (earliestStart == -1 || idx < earliestStart) {
                        earliestStart = idx
                        bestLength = w.length
                    }
                }
            }

            if (earliestStart != -1) {
                var hitEnd = earliestStart + bestLength
                while (hitEnd < collapsed.length && collapsed[hitEnd].isLetterOrDigit()) {
                    hitEnd++
                }

                return buildWindow(
                    text = collapsed,
                    matchStart = earliestStart,
                    matchEnd = hitEnd,
                    targetWindow = targetWindow,
                )
            }
        }

        return null
    }

    private fun buildWindow(
        text: String,
        matchStart: Int,
        matchEnd: Int,
        targetWindow: Int,
    ): String {
        if (text.length <= targetWindow) {
            return text
        }

        val matchLen = matchEnd - matchStart
        val rawLead = text.substring(0, matchStart)
        val rawTail = text.substring(matchEnd)

        val remainingBudget = (targetWindow - matchLen).coerceAtLeast(0)
        val halfBudget = remainingBudget / 2

        val leadBudget: Int
        val tailBudget: Int

        when {
            rawLead.length <= halfBudget -> {
                leadBudget = rawLead.length
                tailBudget = remainingBudget - leadBudget
            }
            rawTail.length <= halfBudget -> {
                tailBudget = rawTail.length
                leadBudget = remainingBudget - tailBudget
            }
            else -> {
                leadBudget = halfBudget
                tailBudget = remainingBudget - leadBudget
            }
        }

        val formattedLead = if (rawLead.length <= leadBudget) {
            rawLead
        } else {
            val slice = rawLead.takeLast(leadBudget)
            val spaceIdx = slice.indexOf(' ')
            if (spaceIdx != -1) {
                "…" + slice.substring(spaceIdx + 1)
            } else {
                "…" + slice
            }
        }

        val formattedTail = if (rawTail.length <= tailBudget) {
            rawTail
        } else {
            val slice = rawTail.take(tailBudget)
            val spaceIdx = slice.lastIndexOf(' ')
            if (spaceIdx != -1) {
                slice.substring(0, spaceIdx) + "…"
            } else {
                slice + "…"
            }
        }

        val matchText = text.substring(matchStart, matchEnd)
        return (formattedLead + matchText + formattedTail).trim()
    }
}
