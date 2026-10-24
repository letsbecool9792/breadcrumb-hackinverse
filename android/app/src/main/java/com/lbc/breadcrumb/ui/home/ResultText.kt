package com.lbc.breadcrumb.ui.home

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.embed.Interpretation
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Pure text presentation rules for search results and memory provenance.
 * Governed by docs/design.md §17. Pure Kotlin tested on the JVM.
 */
object ResultText {

    private val FILLER_WORDS = setOf(
        "a", "an", "the", "that", "this", "these", "those", "it", "its", "i", "me", "my", "we",
        "our", "from", "of", "in", "on", "at", "to", "for", "with", "and", "or", "about", "by",
        "is", "was", "one", "thing", "stuff", "saved", "save", "some", "someone", "sent", "where",
        "what", "which",
    )

    private val MONTH_NAMES = arrayOf(
        "jan", "feb", "mar", "apr", "may", "jun",
        "jul", "aug", "sep", "oct", "nov", "dec",
    )

    data class Fragment(
        val lead: String,
        val hit: String,
        val tail: String,
    )

    /**
     * words(phrase): lowercase, split on anything not a letter or digit, keep words of 2+
     * characters not in the filler set, distinct.
     */
    fun words(phrase: String): List<String> {
        val nonLetterOrDigit = Regex("[^\\p{L}\\p{Nd}]+")
        return phrase.lowercase(Locale.ROOT)
            .split(nonLetterOrDigit)
            .filter { it.length >= 2 && it !in FILLER_WORDS }
            .distinct()
    }

    /**
     * fragment(texts, words, lead = 28, tail = 80): searched in the order rawText, note,
     * extractedText, readText, title, summary, each with whitespace collapsed; the first place any
     * word (longest first, case-insensitive) begins a word (not preceded by a letter or digit); the
     * hit runs to the end of that word ("intern" lights all of "internship"); the lead-in is the last
     * 28 characters before it, cut at a word and prefixed "…"; the tail the next 80, cut at a word
     * with "…". Null when no word appears (a match by meaning).
     */
    fun fragment(
        texts: List<String?>,
        words: List<String>,
        lead: Int = 28,
        tail: Int = 80,
    ): Fragment? {
        if (words.isEmpty()) return null
        val sortedWords = words.sortedByDescending { it.length }
        val whitespaceRegex = Regex("\\s+")

        for (text in texts) {
            if (text.isNullOrBlank()) continue
            val collapsed = text.trim().replace(whitespaceRegex, " ")

            var earliestWordStart = -1
            var bestWordLength = -1

            for (w in sortedWords) {
                if (w.isEmpty()) continue
                var index = 0
                while (index < collapsed.length) {
                    val found = collapsed.indexOf(w, startIndex = index, ignoreCase = true)
                    if (found == -1) break
                    val isWordBoundary = found == 0 || !isLetterOrDigit(collapsed[found - 1])
                    if (isWordBoundary) {
                        if (earliestWordStart == -1 || found < earliestWordStart) {
                            earliestWordStart = found
                            bestWordLength = w.length
                        }
                        break
                    }
                    index = found + 1
                }
            }

            if (earliestWordStart != -1) {
                var hitEnd = earliestWordStart + bestWordLength
                while (hitEnd < collapsed.length && isLetterOrDigit(collapsed[hitEnd])) {
                    hitEnd++
                }
                val hit = collapsed.substring(earliestWordStart, hitEnd)

                val rawLead = collapsed.substring(0, earliestWordStart)
                val formattedLead = if (rawLead.length <= lead) {
                    rawLead
                } else {
                    val candidate = rawLead.takeLast(lead)
                    val spaceIdx = candidate.indexOf(' ')
                    if (spaceIdx != -1) {
                        "…" + candidate.substring(spaceIdx + 1)
                    } else {
                        "…" + candidate
                    }
                }

                val rawTail = collapsed.substring(hitEnd)
                val formattedTail = if (rawTail.length <= tail) {
                    rawTail
                } else {
                    val candidate = rawTail.take(tail)
                    val spaceIdx = candidate.lastIndexOf(' ')
                    if (spaceIdx != -1) {
                        candidate.substring(0, spaceIdx) + "…"
                    } else {
                        candidate + "…"
                    }
                }

                return Fragment(lead = formattedLead, hit = hit, tail = formattedTail)
            }
        }
        return null
    }

    private fun isLetterOrDigit(c: Char): Boolean = Character.isLetterOrDigit(c)

    /**
     * title(memory, summary):
     * LINK: title ?: firstLine(rawText) ?: "Link"
     * TEXT: firstLine(rawText) ?: title ?: "Note"
     * IMAGE: title ?: firstLine(rawText) ?: summary ?: "Image"
     * PDF: title ?: "Document"
     * AUDIO (fallback): title ?: "Audio"
     */
    fun title(memory: Memory, summary: String? = memory.summary): String {
        return when (memory.type) {
            MemoryType.LINK -> memory.title?.takeIf { it.isNotBlank() }
                ?: memory.rawText?.let { firstLine(it) }
                ?: "Link"
            MemoryType.TEXT -> memory.rawText?.let { firstLine(it) }
                ?: memory.title?.takeIf { it.isNotBlank() }
                ?: "Note"
            MemoryType.IMAGE -> memory.title?.takeIf { it.isNotBlank() }
                ?: memory.rawText?.let { firstLine(it) }
                ?: summary?.takeIf { it.isNotBlank() }
                ?: Headline.of(memory)
                ?: "Image"
            MemoryType.PDF -> memory.title?.takeIf { it.isNotBlank() }
                ?: summary?.takeIf { it.isNotBlank() }
                ?: Headline.of(memory)
                ?: "Document"
            MemoryType.AUDIO -> memory.title?.takeIf { it.isNotBlank() }
                ?: "Audio"
        }
    }

    /**
     * firstLine(text, max = 90): the first non-blank line, trimmed;
     * past 90 characters cut at a word with "…".
     */
    fun firstLine(text: String, max: Int = 90): String? {
        val line = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: return null

        if (line.length <= max) return line
        val candidate = line.take(max)
        val spaceIdx = candidate.lastIndexOf(' ')
        return if (spaceIdx != -1) {
            candidate.substring(0, spaceIdx) + "…"
        } else {
            candidate + "…"
        }
    }

    /**
     * opening(text, max): whole when short; else cut at a word with " …".
     */
    fun opening(text: String, max: Int): String {
        val trimmed = text.trim()
        if (trimmed.length <= max) return trimmed
        val candidate = trimmed.take(max)
        val spaceIdx = candidate.lastIndexOf(' ')
        return if (spaceIdx != -1) {
            candidate.substring(0, spaceIdx) + " …"
        } else {
            candidate + " …"
        }
    }

    /**
     * host: the first URL's host, lowercase, without "www.", only when it has a dot.
     */
    fun host(urlOrText: String): String? {
        val urlRegex = Regex("""(?i)\bhttps?://[^\s<>"]+|\bwww\.[^\s<>"]+""")
        val rawUrl = urlRegex.find(urlOrText)?.value ?: return null
        val cleanUrl = rawUrl.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '\'')
        val withScheme = if (!cleanUrl.startsWith("http://", ignoreCase = true) &&
            !cleanUrl.startsWith("https://", ignoreCase = true)
        ) {
            "http://$cleanUrl"
        } else {
            cleanUrl
        }
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val rawHost = uri.host ?: return null
        val lowerHost = rawHost.lowercase(Locale.ROOT)
        val stripped = if (lowerHost.startsWith("www.")) lowerHost.substring(4) else lowerHost
        return if (stripped.contains('.')) stripped else null
    }

    /**
     * age(then, now): "now" under a minute, then "Nm", "Nh", "Nd" (under 7 days),
     * "Nw" (under 30 days), "Nmo" (under a year), "Ny".
     */
    fun age(then: Long, now: Long): String {
        val diffSeconds = (now - then).coerceAtLeast(0) / 1000
        val minutes = diffSeconds / 60
        if (minutes < 1) return "now"
        val hours = minutes / 60
        if (hours < 1) return "${minutes}m"
        val days = hours / 24
        if (days < 1) return "${hours}h"
        if (days < 7) return "${days}d"
        val weeks = days / 7
        if (days < 30) return "${weeks}w"
        val months = days / 30
        if (days < 365) return "${months}mo"
        val years = days / 365
        return "${years}y"
    }

    /**
     * tileMeta: age · source lowercase.
     */
    fun tileMeta(memory: Memory, now: Long = System.currentTimeMillis()): String {
        val a = age(memory.capturedAt, now)
        val source = memory.sourceAppLabel?.lowercase(Locale.ROOT)
        return listOfNotNull(a, source).joinToString(" · ")
    }

    /**
     * rowMeta: age · source · chips joined " + " lowercase ("2d · whatsapp · image + link").
     */
    fun rowMeta(memory: Memory, now: Long = System.currentTimeMillis()): String {
        val a = age(memory.capturedAt, now)
        val source = memory.sourceAppLabel?.lowercase(Locale.ROOT)
        val chipString = memory.chips.joinToString(" + ") { it.name.lowercase(Locale.ROOT) }
        return listOfNotNull(a, source, chipString.takeIf { it.isNotEmpty() }).joinToString(" · ")
    }

    /**
     * status(memory): extractedText null and a LINK → "page not read yet"; null
     * and an IMAGE or PDF with a stored file → "still reading it"; else null.
     */
    fun status(memory: Memory): String? {
        val parts = mutableListOf<String>()

        if (memory.extractedText == null) {
            when {
                memory.type == MemoryType.LINK -> parts.add("page not read yet")
                (memory.type == MemoryType.IMAGE || memory.type == MemoryType.PDF) && memory.localUri != null -> {
                    parts.add("still reading it")
                }
            }
        }

        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }

    /** kept(total): "17 kept". */
    fun kept(total: Int): String = "$total kept"

    /**
     * dates(from, to, today): a whole calendar month → "apr" ("apr 2025" when not this year);
     * one day → "18 sep"; a range → "1 apr – 15 may"; only from → "since 1 apr"; only to → "until 30 apr".
     * Years only when not this year.
     */
    fun dates(from: String?, to: String?, today: LocalDate = LocalDate.now()): String? {
        if (from == null && to == null) return null
        val fromDate = from?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val toDate = to?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        if (fromDate != null && toDate != null) {
            val isWholeMonth = fromDate.dayOfMonth == 1 &&
                toDate.dayOfMonth == toDate.lengthOfMonth() &&
                fromDate.year == toDate.year &&
                fromDate.month == toDate.month

            if (isWholeMonth) {
                val m = monthOf(fromDate)
                return if (fromDate.year == today.year) m else "$m ${fromDate.year}"
            }

            if (fromDate == toDate) {
                return formatDay(fromDate, today.year)
            }

            val fromStr = formatDay(fromDate, today.year)
            val toStr = formatDay(toDate, today.year)
            return "$fromStr – $toStr"
        }

        if (fromDate != null) {
            return "since " + formatDay(fromDate, today.year)
        }

        if (toDate != null) {
            return "until " + formatDay(toDate, today.year)
        }

        return null
    }

    private fun formatDay(date: LocalDate, currentYear: Int): String {
        val d = date.dayOfMonth
        val m = monthOf(date)
        return if (date.year == currentYear) "$d $m" else "$d $m ${date.year}"
    }

    /**
     * monthOf: "apr".
     */
    fun monthOf(date: LocalDate): String {
        return MONTH_NAMES[date.monthValue - 1]
    }

    /**
     * taken: the day alone, "18 apr".
     */
    fun taken(dateMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(dateMillis).atZone(zoneId).toLocalDate()
        return "${date.dayOfMonth} ${monthOf(date)}"
    }

    /**
     * saved(then, now): "12 sep · " + today / yesterday / N days ago (under 14) /
     * N weeks ago (under 60 days) / N months ago (under 730 days) / N years ago.
     */
    fun saved(
        then: Long,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val thenDate = Instant.ofEpochMilli(then).atZone(zoneId).toLocalDate()
        val nowDate = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()

        val prefix = "${thenDate.dayOfMonth} ${monthOf(thenDate)}"
        val days = ChronoUnit.DAYS.between(thenDate, nowDate).coerceAtLeast(0)

        val relative = when {
            days == 0L -> "today"
            days == 1L -> "yesterday"
            days < 14L -> "$days days ago"
            days < 60L -> "${days / 7} weeks ago"
            days < 730L -> "${days / 30} months ago"
            else -> "${days / 365} years ago"
        }

        return "$prefix · $relative"
    }

    /**
     * counter(shown, total, typed, interpretation, today, status):
     * "{shown} of {total}", then the phrase typed when there is no interpretation;
     * otherwise the query (when not blank), the types ("images", "links", "pdfs", "notes", joined " or "),
     * the dates, and "from {source lowercase}"; then the status.
     * Joined " · ": "1 of 17 · internship · images · apr".
     */
    fun counter(
        shown: Int,
        total: Int,
        typed: String,
        interpretation: Interpretation?,
        today: LocalDate = LocalDate.now(),
        status: SearchStatus? = null,
    ): String {
        val parts = mutableListOf<String>()
        parts.add("$shown of $total")

        if (interpretation == null) {
            val trimmedTyped = typed.trim()
            if (trimmedTyped.isNotEmpty()) {
                parts.add(trimmedTyped)
            }
        } else {
            val q = interpretation.query.trim()
            if (q.isNotEmpty()) {
                parts.add(q)
            }

            if (interpretation.types.isNotEmpty()) {
                val mappedTypes = interpretation.types.map { t ->
                    when (t.uppercase(Locale.ROOT)) {
                        "IMAGE" -> "images"
                        "LINK" -> "links"
                        "PDF" -> "pdfs"
                        "TEXT" -> "notes"
                        else -> t.lowercase(Locale.ROOT)
                    }
                }
                parts.add(mappedTypes.joinToString(" or "))
            }

            val d = dates(interpretation.from, interpretation.to, today)
            if (d != null) {
                parts.add(d)
            }

            if (!interpretation.sourceApp.isNullOrBlank()) {
                parts.add("from ${interpretation.sourceApp.lowercase(Locale.ROOT)}")
            }
        }

        when (status) {
            SearchStatus.OFFLINE -> parts.add("offline · words only")
            SearchStatus.UNAVAILABLE -> parts.add("search busy · words only")
            SearchStatus.RANKING, SearchStatus.RANKED, null -> { /* add nothing */ }
        }

        return parts.joinToString(" · ")
    }
}
