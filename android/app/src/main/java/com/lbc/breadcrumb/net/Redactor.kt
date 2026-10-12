package com.lbc.breadcrumb.net

import java.util.regex.Pattern

/**
 * Span identifying a text range to redact with a tag like [phone] or [aadhaar].
 */
data class RedactionSpan(val start: Int, val end: Int, val tag: String)

/**
 * On-device entity extraction (ML Kit Entity Extraction). [extract] answers
 * null when it could not read the text: the model isn't downloaded yet, or
 * annotating failed or timed out. Then the text doesn't go.
 */
fun interface EntityExtractor {
    fun extract(text: String): List<RedactionSpan>?

    /** Asks for the model, on any network, so it is ready by the time something goes. */
    fun prepare() {}
}

/**
 * Long text is annotated in pieces, each with its own time limit, so one long
 * PDF can't time the whole memory out.
 */
object EntityChunks {

    const val SIZE = 2_000

    /** [text] in pieces of at most [size], each cut at the last whitespace before the limit when there is one. */
    fun pieces(text: String, size: Int = SIZE): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + size, text.length)
            if (end < text.length) {
                val cut = text.lastIndexOfAny(charArrayOf(' ', '\n'), end - 1)
                if (cut > start) end = cut + 1
            }
            out += start to text.substring(start, end)
            start = end
        }
        return out
    }

    /** Reads each piece and moves its spans to [text]'s offsets; null when any piece could not be read. */
    fun annotate(text: String, size: Int = SIZE, read: (String) -> List<RedactionSpan>?): List<RedactionSpan>? {
        val spans = mutableListOf<RedactionSpan>()
        for ((offset, piece) in pieces(text, size)) {
            val found = read(piece) ?: return null
            found.mapTo(spans) { RedactionSpan(it.start + offset, it.end + offset, it.tag) }
        }
        return spans
    }
}

/**
 * L4 Redactor: replaces personal details with their type tag before sending:
 * [phone], [email], [address], [card], [iban], [tracking], [otp], [aadhaar], [pan], [key].
 *
 * Uses ML Kit Entity Extraction on the phone for phones, emails, addresses,
 * cards, IBANs, and tracking numbers.
 *
 * Uses regexes for OTPs, Aadhaar (12 digits, with or without spaces), PAN (5 letters,
 * 4 digits, 1 letter), API keys and long random tokens, plus regex fallbacks.
 *
 * Dates stay. A link keeps its scheme, host and path, nothing more ([stripUrl]).
 * Never changes the phone's own Room copy.
 */
object Redactor {

    /** Set by BreadcrumbApp on Android to use ML Kit Entity Extraction. */
    var deviceExtractor: EntityExtractor? = null

    // Preserved Patterns (URLs and Dates must never be redacted)
    private val URL_REGEX = Pattern.compile(
        """https?://[^\s<>"{}|\\^`]+""",
        Pattern.CASE_INSENSITIVE,
    )

    private val DATE_REGEXES = listOf(
        // ISO 8601: 2026-10-11, 2026/10/11
        Pattern.compile("""\b\d{4}[-/]\d{2}[-/]\d{2}(?:T\d{2}:\d{2}(?::\d{2})?(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})?)?\b"""),
        // Common formats: 11/10/2026, 11-10-2026
        Pattern.compile("""\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b"""),
        // Month names: April 30, 30 April 2026, Apr 30, 2026
        Pattern.compile(
            """\b(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\s+\d{1,2}(?:st|nd|rd|th)?(?:,?\s+\d{4})?\b""",
            Pattern.CASE_INSENSITIVE,
        ),
        Pattern.compile(
            """\b\d{1,2}(?:st|nd|rd|th)?\s+(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)(?:,?\s+\d{4})?\b""",
            Pattern.CASE_INSENSITIVE,
        ),
        // Times: 10:30 AM, 15:45
        Pattern.compile("""\b\d{1,2}:\d{2}(?::\d{2})?\s*(?:AM|PM|am|pm)?\b"""),
    )

    // Regexes for specific credentials & identity numbers
    // Aadhaar: 12 digits, with or without spaces/hyphens
    private val AADHAAR_REGEX = Pattern.compile("""\b(?:\d{4}[\s-]\d{4}[\s-]\d{4}|\d{12})\b""")

    // PAN: 5 letters, 4 digits, 1 letter
    private val PAN_REGEX = Pattern.compile("""\b[A-Za-z]{5}\d{4}[A-Za-z]\b""")

    // OTPs: 4-8 digits preceded or followed by OTP/code keywords
    private val OTP_PREFIX_REGEX = Pattern.compile(
        """(?i)\b(?:otp|one[-\s]time\s*password|verification\s*code|security\s*code|login\s*code|passcode|code|pin)\b(?:\s+is)?[\s:=-]*(\d{4,8})\b""",
    )
    private val OTP_SUFFIX_REGEX = Pattern.compile(
        """\b(\d{4,8})\b[\s:=-]*(?i:is\s+(?:your\s+)?(?:otp|code|pin|verification\s*code|one[-\s]time\s*password))\b""",
    )
    private val OTP_VERIFY_REGEX = Pattern.compile(
        """(?i)\buse\s+(\d{4,8})\s+(?:to\s+(?:verify|login|log\s+in|confirm)|as\s+(?:your\s+)?(?:otp|code))\b""",
    )

    // API keys and long random tokens
    private val API_KEY_PREFIXES = Pattern.compile(
        """\b(?:sk-[a-zA-Z0-9_-]{20,}|AIza[0-9A-Za-z-_]{35}|(?:ghp|gho|ghu|ghs|ghr)_[a-zA-Z0-9]{36}|github_pat_[a-zA-Z0-9_]{50,}|AKIA[0-9A-Z]{16}|xox[baprs]-[a-zA-Z0-9-]{10,}|eyJ[a-zA-Z0-9_-]{10,}\.eyJ[a-zA-Z0-9_-]{10,}\.[a-zA-Z0-9_-]{10,})\b""",
    )
    private val CONTEXTUAL_KEY_REGEX = Pattern.compile(
        """(?i)\b(?:api[_-]?key|secret|token|bearer|auth[_-]?token|access[_-]?token)\b[\s:=]+["']?([a-zA-Z0-9_.\-]{16,})["']?""",
    )
    private val HEX_KEY_REGEX = Pattern.compile("""\b[0-9a-fA-F]{32,64}\b""")
    private val HIGH_ENTROPY_KEY_REGEX = Pattern.compile("""\b(?=[a-zA-Z0-9_-]{32,}\b)(?=.*[0-9])(?=.*[a-zA-Z])[a-zA-Z0-9_-]{32,}\b""")

    // Fallback regexes (also used on JVM tests)
    private val EMAIL_REGEX = Pattern.compile("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")
    private val PHONE_INDIAN_REGEX = Pattern.compile("""(?:\+91[\s-]?)?[6-9]\d{4}[\s-]?\d{5}\b""")
    private val PHONE_INTL_REGEX = Pattern.compile("""\+\d{1,3}[\s-]?(?:\(?\d{2,4}\)?[\s-]?)?\d{3,5}[\s-]?\d{3,5}\b""")
    private val CARD_REGEX = Pattern.compile("""\b(?:\d{4}[-\s]){3}\d{4}\b|\b\d{16}\b""")
    private val TRACKING_REGEX = Pattern.compile("""\b(?:1Z[0-9A-Z]{16}|[A-Z]{2}\d{9}[A-Z]{2})\b""")
    private val IBAN_REGEX = Pattern.compile("""\b[A-Z]{2}\d{2}(?:[\s]?[A-Z0-9]{4}){3,7}(?:[\s]?[A-Z0-9]{1,4})?\b""")

    /** [text] stripped by the rules alone (the regexes), without the phone's entity model. */
    fun redact(text: String): String = redact(text, emptyList())

    /**
     * What goes to the server for [text]: stripped by the phone's entity model
     * and the rules, or null when the model could not read it (not downloaded
     * yet, a failure or a timeout). Null means the memory doesn't go this time:
     * redaction fails closed, since addresses have no rule to fall back on.
     */
    fun forUpload(text: String, extractor: EntityExtractor? = deviceExtractor): String? {
        val spans = try {
            extractor?.extract(text)
        } catch (_: Exception) {
            null
        } ?: return null
        return redact(text, spans)
    }

    fun redact(text: String, additionalSpans: List<RedactionSpan>): String {
        if (text.isEmpty()) return text

        // 1. Identify all preserved ranges (URLs and Dates)
        val preservedRanges = mutableListOf<IntRange>()
        val urlMatcher = URL_REGEX.matcher(text)
        while (urlMatcher.find()) {
            preservedRanges.add(urlMatcher.start() until urlMatcher.end())
        }
        for (datePattern in DATE_REGEXES) {
            val dateMatcher = datePattern.matcher(text)
            while (dateMatcher.find()) {
                preservedRanges.add(dateMatcher.start() until dateMatcher.end())
            }
        }

        fun isPreserved(start: Int, end: Int): Boolean {
            return preservedRanges.any { it.first < end && start < it.last }
        }

        val spans = mutableListOf<RedactionSpan>()

        // Add additional/device spans (from ML Kit on device or test fakes)
        for (span in additionalSpans) {
            if (!isPreserved(span.start, span.end)) {
                spans.add(span)
            }
        }

        // OTPs
        addRegexGroupMatches(text, OTP_PREFIX_REGEX, 1, "[otp]", spans, ::isPreserved)
        addRegexGroupMatches(text, OTP_SUFFIX_REGEX, 1, "[otp]", spans, ::isPreserved)
        addRegexGroupMatches(text, OTP_VERIFY_REGEX, 1, "[otp]", spans, ::isPreserved)

        // Identity numbers: Aadhaar & PAN
        addRegexMatches(text, AADHAAR_REGEX, "[aadhaar]", spans, ::isPreserved)
        addRegexMatches(text, PAN_REGEX, "[pan]", spans, ::isPreserved)

        // API keys & tokens
        addRegexMatches(text, API_KEY_PREFIXES, "[key]", spans, ::isPreserved)
        addRegexGroupMatches(text, CONTEXTUAL_KEY_REGEX, 1, "[key]", spans, ::isPreserved)
        addRegexMatches(text, HEX_KEY_REGEX, "[key]", spans, ::isPreserved)
        addRegexMatches(text, HIGH_ENTROPY_KEY_REGEX, "[key]", spans, ::isPreserved)

        // Fallback patterns for phones, emails, cards, IBANs, tracking
        addRegexMatches(text, EMAIL_REGEX, "[email]", spans, ::isPreserved)
        addRegexMatches(text, PHONE_INDIAN_REGEX, "[phone]", spans, ::isPreserved)
        addRegexMatches(text, PHONE_INTL_REGEX, "[phone]", spans, ::isPreserved)
        addRegexMatches(text, CARD_REGEX, "[card]", spans, ::isPreserved)
        addRegexMatches(text, TRACKING_REGEX, "[tracking]", spans, ::isPreserved)
        addRegexMatches(text, IBAN_REGEX, "[iban]", spans, ::isPreserved)

        // Sort spans: earliest start first, longest length first
        val sorted = spans.sortedWith(
            compareBy<RedactionSpan> { it.start }
                .thenByDescending { it.end - it.start },
        )

        // Filter out overlapping spans
        val nonOverlapping = mutableListOf<RedactionSpan>()
        var lastEnd = -1
        for (span in sorted) {
            if (span.start >= lastEnd) {
                nonOverlapping.add(span)
                lastEnd = span.end
            }
        }

        // Reconstruct string with tags
        val sb = StringBuilder()
        var cursor = 0
        for (span in nonOverlapping) {
            if (span.start > cursor) {
                sb.append(text.substring(cursor, span.start))
            }
            sb.append(span.tag)
            cursor = span.end
        }
        if (cursor < text.length) {
            sb.append(text.substring(cursor))
        }
        return stripUrls(sb.toString())
    }

    /** Every link in [text] through [stripUrl]. Links were kept whole until now, so no rule cut into them. */
    private fun stripUrls(text: String): String {
        val matcher = URL_REGEX.matcher(text)
        val out = StringBuilder()
        var cursor = 0
        while (matcher.find()) {
            out.append(text, cursor, matcher.start()).append(stripUrl(matcher.group()))
            cursor = matcher.end()
        }
        return out.append(text, cursor, text.length).toString()
    }

    /**
     * A link as it goes: its scheme, host and path. The query string and the
     * fragment carry tokens, emails, and reset and share links, and so does a
     * `user:password@`; a long random path segment is an id, and becomes [key].
     * The server needs only the host, for the sites a search may name.
     */
    internal fun stripUrl(url: String): String {
        val bare = url.substringBefore('#').substringBefore('?')
        val schemeEnd = bare.indexOf("://")
        if (schemeEnd < 0) return bare
        val rest = bare.substring(schemeEnd + 3)
        val slash = rest.indexOf('/')
        val host = (if (slash < 0) rest else rest.substring(0, slash)).substringAfterLast('@')
        val path = if (slash < 0) "" else rest.substring(slash).split('/').joinToString("/") { if (isToken(it)) "[key]" else it }
        return bare.substring(0, schemeEnd + 3) + host + path
    }

    /** A path segment that is an id, not words: long, letters and digits mixed, and not a slug of short words. */
    private fun isToken(segment: String): Boolean {
        if (segment.length < 20) return false
        if (API_KEY_PREFIXES.matcher(segment).matches() || HEX_KEY_REGEX.matcher(segment).matches()) return true
        val parts = segment.split('-', '_')
        val slug = parts.size >= 3 && parts.all { it.length <= 12 }
        return !slug && segment.any(Char::isDigit) && segment.any(Char::isLetter)
    }

    private fun addRegexMatches(
        text: String,
        pattern: Pattern,
        tag: String,
        out: MutableList<RedactionSpan>,
        isPreserved: (Int, Int) -> Boolean,
    ) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            if (!isPreserved(start, end)) {
                out.add(RedactionSpan(start, end, tag))
            }
        }
    }

    private fun addRegexGroupMatches(
        text: String,
        pattern: Pattern,
        groupIndex: Int,
        tag: String,
        out: MutableList<RedactionSpan>,
        isPreserved: (Int, Int) -> Boolean,
    ) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val start = matcher.start(groupIndex)
            val end = matcher.end(groupIndex)
            if (start in 0 until end && !isPreserved(start, end)) {
                out.add(RedactionSpan(start, end, tag))
            }
        }
    }
}
