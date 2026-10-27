package com.lbc.breadcrumb.embed

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * A search phrase taken apart on the phone, by rules (local-first L6): the
 * kinds, days and source it names, and the words left to search. What the
 * server's POST /parse asked Gemini for, without the phrase leaving the phone,
 * and in microseconds. Pure.
 *
 * Every word the filters use is taken out of the query, and so are the words
 * that only make it a sentence ("that", "from", "i saved"): the word search
 * wants every word of the query in a memory.
 */
object PhraseRules {

    fun parse(phrase: String, today: LocalDate, sources: List<String>): Interpretation {
        val words = WORD.findAll(phrase.lowercase()).map { it.value }.toList()
        val used = BooleanArray(words.size)
        val source = sourceOf(words, used, sources)
        val types = typesOf(words, used)
        val days = daysOf(words, used, today)
        val query = words.filterIndexed { i, word -> !used[i] && word !in FILLER }.joinToString(" ")
        return Interpretation(query, types, days?.start?.toString(), days?.endInclusive?.toString(), source)
    }

    private val WORD = Regex("""[\p{L}\p{N}]+""")

    // Sources: the apps saves were shared from and the sites they link to, as Sites.sourcesOf names them.

    private val ALIASES = mapOf(
        "insta" to "instagram", "ig" to "instagram", "yt" to "youtube", "twitter" to "x", "tweet" to "x",
        "tweets" to "x", "fb" to "facebook", "wa" to "whatsapp",
    )

    /** Apps named with everyday words: one is the source only as "photos app" or "from photos". */
    private val PLAIN_NAMES = setOf(
        "photos", "files", "gallery", "camera", "messages", "notes", "drive", "keep", "phone", "mail", "email",
        "calendar", "contacts", "clock", "maps", "news", "music", "books", "docs", "sheets", "slides", "meet",
        "chat", "home", "translate", "lens", "assistant", "settings", "browser", "x",
    )

    private val SOURCE_PREPOSITIONS = setOf("from", "on", "in", "via")

    private fun sourceOf(words: List<String>, used: BooleanArray, sources: List<String>): String? {
        val named = sources.map { it to WORD.findAll(it.lowercase()).map { m -> m.value }.toList() }
        for (i in words.indices) {
            for ((source, parts) in named) {
                if (parts.size > 1 && words.subList(i, minOf(i + parts.size, words.size)) == parts) {
                    return take(words, used, i, parts.size, source)
                }
            }
            val word = words[i]
            val name = ALIASES[word] ?: word
            // "Teams" names "Microsoft Teams"; "photos" alone never names "Google Photos".
            val source = named.firstOrNull { (_, parts) ->
                parts == listOf(name) || (parts.size > 1 && parts.last() == name && name !in PLAIN_NAMES && name.length >= 4)
            }?.first ?: continue
            val plain = word == name && name in PLAIN_NAMES
            if (plain && words.getOrNull(i + 1) != "app" && words.getOrNull(i - 1) !in SOURCE_PREPOSITIONS) continue
            return take(words, used, i, 1, source)
        }
        return null
    }

    private fun take(words: List<String>, used: BooleanArray, at: Int, length: Int, source: String): String {
        for (k in at until at + length) used[k] = true
        if (words.getOrNull(at + length) == "app") used[at + length] = true
        return source
    }

    // Kinds, as the server's prompt names them.

    private val TYPE_WORDS = mapOf(
        "IMAGE" to setOf(
            "screenshot", "screenshots", "ss", "screengrab", "photo", "photos", "pic", "pics", "picture",
            "pictures", "image", "images", "meme", "memes", "selfie", "selfies", "wallpaper", "wallpapers",
        ),
        "PDF" to setOf("pdf", "pdfs", "document", "documents", "doc", "docs"),
        "LINK" to setOf(
            "link", "links", "url", "urls", "article", "articles", "video", "videos", "website", "websites",
            "webpage", "blog", "reel", "reels",
        ),
        "TEXT" to setOf("note", "notes", "message", "messages", "msg", "msgs"),
        "AUDIO" to setOf("audio", "recording", "recordings", "voicenote", "voicenotes"),
    )

    /** "voice note", "audio message", "voice memo": one kind, not a note and a sound. */
    private val VOICE = setOf("voice", "audio")
    private val VOICE_KINDS = setOf("note", "notes", "message", "messages", "memo", "memos", "recording", "recordings", "clip", "clips")

    private fun typesOf(words: List<String>, used: BooleanArray): List<String> {
        val types = linkedSetOf<String>()
        for (i in words.indices) {
            if (used[i]) continue
            if (words[i] == "screen" && words.getOrNull(i + 1) in setOf("shot", "shots")) {
                types += "IMAGE"
                used[i] = true
                used[i + 1] = true
                continue
            }
            if (words[i] in VOICE && words.getOrNull(i + 1) in VOICE_KINDS) {
                types += "AUDIO"
                used[i] = true
                used[i + 1] = true
                continue
            }
            val type = TYPE_WORDS.entries.firstOrNull { words[i] in it.value }?.key ?: continue
            types += type
            used[i] = true
        }
        return types.toList()
    }

    // Days: every range named, joined into one; never past today.

    private fun daysOf(words: List<String>, used: BooleanArray, today: LocalDate): ClosedRange<LocalDate>? {
        val found = mutableListOf<ClosedRange<LocalDate>>()
        var since = false
        var i = 0
        while (i < words.size) {
            val match = if (used[i]) null else dateAt(words, i, today)
            if (match == null) {
                i++
                continue
            }
            val (range, length) = match
            for (k in i until i + length) used[k] = true
            if (words.getOrNull(i - 1) == "since") {
                since = true
                used[i - 1] = true
            }
            found += range
            i += length
        }
        if (found.isEmpty()) return null
        val from = found.minOf { it.start }
        val to = if (since) today else found.maxOf { it.endInclusive }
        return from..minOf(to, today).coerceAtLeast(from)
    }

    private fun day(date: LocalDate) = date..date

    /** The days named by the words at [i], and how many words name them; null when none start there. */
    private fun dateAt(words: List<String>, i: Int, today: LocalDate): Pair<ClosedRange<LocalDate>, Int>? {
        val word = words[i]
        val next = words.getOrNull(i + 1)
        when {
            word == "day" && next == "before" && words.getOrNull(i + 2) == "yesterday" -> return day(today.minusDays(2)) to 3
            word == "today" || word == "tonight" -> return day(today) to 1
            word == "yesterday" -> return day(today.minusDays(1)) to 1
        }
        if (word in RELATIVE && next != null) relative(word, next, today)?.let { return it to 2 }
        // "two weeks ago", "a couple of days ago"
        count(words, i)?.let { (n, length) ->
            val span = words.getOrNull(i + length)?.let(::spanOf)
            if (span != null && words.getOrNull(i + length + 1) == "ago") return ago(n, span, today) to length + 2
        }
        // "last 3 days", "past few weeks"
        if ((word == "last" || word == "past") && next != null) {
            count(words, i + 1)?.let { (n, length) ->
                val span = words.getOrNull(i + 1 + length)?.let(::spanOf)
                if (span != null) return minus(today, n, span)..today to length + 2
            }
        }
        weekday(word)?.let { return day(today.with(TemporalAdjusters.previousOrSame(it))) to 1 }
        monthAt(words, i, today)?.let { return it }
        dayNumber(word)?.let { d ->
            // "3rd may", "3 of may 2025"
            val at = if (next == "of") i + 2 else i + 1
            val month = words.getOrNull(at)?.let(::monthOf)
            if (month != null) {
                val year = words.getOrNull(at + 1)?.let(::yearOf)
                val date = dated(month, d, year, today) ?: return null
                return day(date) to at - i + 1 + (if (year != null) 1 else 0)
            }
            // "on the 5th": that day of this month, or of the last while it's still to come.
            if (!word.all(Char::isDigit) && words.getOrNull(i - 1) in setOf("the", "on")) {
                val yearMonth = YearMonth.from(today).let { if (d > today.dayOfMonth) it.minusMonths(1) else it }
                if (yearMonth.isValidDay(d)) return day(yearMonth.atDay(d)) to 1
            }
        }
        yearOf(word)?.let { y ->
            // "from 2025"; a bare year is left to the words ("2025 tax return").
            if (words.getOrNull(i - 1) in YEAR_PREPOSITIONS) return LocalDate.of(y, 1, 1)..LocalDate.of(y, 12, 31) to 1
            val month = next?.let(::monthOf)
            if (month != null) return YearMonth.of(y, month).let { it.atDay(1)..it.atEndOfMonth() } to 2
        }
        return null
    }

    private val RELATIVE = setOf("this", "last", "past", "previous")

    /** "this week", "last weekend", "past month", "last monday", "last night". */
    private fun relative(which: String, unit: String, today: LocalDate): ClosedRange<LocalDate>? {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return when (unit) {
            "night" -> if (which == "last") day(today.minusDays(1)) else null
            "week" -> when (which) {
                "this" -> monday..today
                "past" -> today.minusDays(7)..today
                else -> monday.minusDays(7)..monday.minusDays(1)
            }
            "weekend" -> {
                // On a Saturday or Sunday, "last weekend" is the one before.
                val saturday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
                val inOne = today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY
                val start = if (which != "this" && inOne) saturday.minusDays(7) else saturday
                start..start.plusDays(1)
            }
            "month" -> when (which) {
                "this" -> today.withDayOfMonth(1)..today
                "past" -> today.minusDays(30)..today
                else -> YearMonth.from(today).minusMonths(1).let { it.atDay(1)..it.atEndOfMonth() }
            }
            "year" -> when (which) {
                "this" -> today.withDayOfYear(1)..today
                "past" -> today.minusYears(1)..today
                else -> today.minusYears(1).let { it.withDayOfYear(1)..it.withDayOfYear(it.lengthOfYear()) }
            }
            else -> weekday(unit)?.let { dow ->
                day(today.with(if (which == "this") TemporalAdjusters.previousOrSame(dow) else TemporalAdjusters.previous(dow)))
            }
        }
    }

    private enum class Span { DAY, WEEK, MONTH, YEAR }

    private fun spanOf(word: String) = when (word) {
        "day", "days" -> Span.DAY
        "week", "weeks" -> Span.WEEK
        "month", "months" -> Span.MONTH
        "year", "years" -> Span.YEAR
        else -> null
    }

    private fun minus(date: LocalDate, n: Int, span: Span): LocalDate = when (span) {
        Span.DAY -> date.minusDays(n.toLong())
        Span.WEEK -> date.minusWeeks(n.toLong())
        Span.MONTH -> date.minusMonths(n.toLong())
        Span.YEAR -> date.minusYears(n.toLong())
    }

    /** "3 days ago" is that day; "two weeks ago" that whole week, Monday to Sunday; months and years whole too. */
    private fun ago(n: Int, span: Span, today: LocalDate): ClosedRange<LocalDate> {
        val then = minus(today, n, span)
        return when (span) {
            Span.DAY -> day(then)
            Span.WEEK -> then.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it..it.plusDays(6) }
            Span.MONTH -> YearMonth.from(then).let { it.atDay(1)..it.atEndOfMonth() }
            Span.YEAR -> then.withDayOfYear(1)..then.withDayOfYear(then.lengthOfYear())
        }
    }

    private val NUMBERS = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "few" to 3,
    )

    /** A count at [i], and how many words it takes: "3", "two", "a couple of". */
    private fun count(words: List<String>, i: Int): Pair<Int, Int>? {
        val word = words.getOrNull(i) ?: return null
        val next = words.getOrNull(i + 1)
        if (word == "couple") return 2 to if (next == "of") 2 else 1
        if (word == "a" && next == "couple") return 2 to if (words.getOrNull(i + 2) == "of") 3 else 2
        if (word == "a" && next == "few") return 3 to 2
        NUMBERS[word]?.let { return it to 1 }
        return word.toIntOrNull()?.takeIf { it in 1..99 }?.let { it to 1 }
    }

    private fun weekday(word: String) = when (word) {
        "monday" -> DayOfWeek.MONDAY
        "tuesday" -> DayOfWeek.TUESDAY
        "wednesday" -> DayOfWeek.WEDNESDAY
        "thursday" -> DayOfWeek.THURSDAY
        "friday" -> DayOfWeek.FRIDAY
        "saturday" -> DayOfWeek.SATURDAY
        "sunday" -> DayOfWeek.SUNDAY
        else -> null
    }

    private val MONTHS = mapOf(
        "january" to 1, "jan" to 1, "february" to 2, "feb" to 2, "march" to 3, "april" to 4, "apr" to 4,
        "may" to 5, "june" to 6, "jun" to 6, "july" to 7, "jul" to 7, "august" to 8, "aug" to 8,
        "september" to 9, "sept" to 9, "sep" to 9, "october" to 10, "oct" to 10, "november" to 11, "nov" to 11,
        "december" to 12, "dec" to 12,
    )

    private fun monthOf(word: String) = MONTHS[word]

    private val YEAR_PREPOSITIONS = setOf("from", "in", "during", "since", "of")
    private val MONTH_PREPOSITIONS = YEAR_PREPOSITIONS + setOf("early", "mid", "late", "until", "before", "after", "last")

    /**
     * "april" (the latest April up to today), "may 3rd", "march 2025". "may" is
     * the month only where it can't be the verb, and "april fools" is no date.
     */
    private fun monthAt(words: List<String>, i: Int, today: LocalDate): Pair<ClosedRange<LocalDate>, Int>? {
        val month = monthOf(words[i]) ?: return null
        val next = words.getOrNull(i + 1)
        if (next == "fool" || next == "fools") return null
        val year = next?.let(::yearOf)
        val d = next?.let(::dayNumber)
        if (words[i] == "may") {
            val prev = words.getOrNull(i - 1)
            val isMonth = year != null || d != null || prev in MONTH_PREPOSITIONS || prev?.let(::dayNumber) != null ||
                (i == words.lastIndex && words.size > 1)
            if (!isMonth) return null
        }
        if (year != null) return YearMonth.of(year, month).let { it.atDay(1)..it.atEndOfMonth() } to 2
        if (d != null) {
            val y = words.getOrNull(i + 2)?.let(::yearOf)
            val date = dated(month, d, y, today) ?: return null
            return day(date) to if (y != null) 3 else 2
        }
        val latest = YearMonth.of(today.year, month).let { if (month > today.monthValue) it.minusYears(1) else it }
        return latest.atDay(1)..latest.atEndOfMonth() to 1
    }

    /** That day in [year], or else the latest one up to today. */
    private fun dated(month: Int, d: Int, year: Int?, today: LocalDate): LocalDate? {
        val date = runCatching { LocalDate.of(year ?: today.year, month, d) }.getOrNull() ?: return null
        return if (year == null && date > today) runCatching { date.minusYears(1) }.getOrNull() else date
    }

    private val ORDINAL = Regex("""(\d{1,2})(st|nd|rd|th)?""")

    private fun dayNumber(word: String): Int? =
        ORDINAL.matchEntire(word)?.groupValues?.get(1)?.toInt()?.takeIf { it in 1..31 }

    private fun yearOf(word: String): Int? =
        word.takeIf { it.length == 4 }?.toIntOrNull()?.takeIf { it in 1990..2100 }

    /** Words that only make the phrase a sentence. */
    private val FILLER = setOf(
        "a", "an", "the", "that", "this", "those", "these", "there", "i", "me", "my", "mine", "we", "our", "us",
        "you", "your", "he", "she", "they", "them", "his", "her", "their", "it", "its", "of", "from", "on", "in",
        "at", "for", "to", "with", "about", "by", "via", "and", "or", "but", "some", "something", "stuff", "thing",
        "things", "what", "which", "who", "did", "do", "does", "saved", "save", "sent", "send", "shared", "share",
        "got", "get", "someone", "somebody", "person", "guy", "was", "were", "is", "are", "be", "been", "one",
        "ones", "find", "show", "search", "where", "when", "any", "all", "app", "ago", "early", "mid", "late",
        "since", "during", "last", "past", "previous", "recent", "recently", "had", "have", "has", "see", "saw",
        // "may" that isn't the month is the verb.
        "may", "might", "could", "would", "should",
    )
}
