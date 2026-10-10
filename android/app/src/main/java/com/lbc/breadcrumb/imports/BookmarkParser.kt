package com.lbc.breadcrumb.imports

import java.io.InputStream
import java.util.regex.Pattern

data class ParsedBookmark(
    val url: String,
    val title: String?,
    val addDateMillis: Long?,
)

/**
 * Parses Netscape HTML bookmark exports produced by browsers (Chrome, Firefox, Safari, Edge).
 *
 * Each bookmark is an `<A HREF="..." ADD_DATE="...">Title</A>` tag.
 * - Extracts `url` from `HREF`.
 * - Converts `ADD_DATE` (seconds since epoch) to epoch milliseconds.
 * - Preserves the bookmark title with HTML entities unescaped and inner tags stripped.
 */
object BookmarkParser {

    private val A_TAG_REGEX = Pattern.compile("<a\\s+([^>]+)>(.*?)</a>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    private val HREF_REGEX = Pattern.compile("href\\s*=\\s*[\"']?([^\"'\\s>]+)", Pattern.CASE_INSENSITIVE)
    private val ADD_DATE_REGEX = Pattern.compile("add_date\\s*=\\s*[\"']?(\\d+)", Pattern.CASE_INSENSITIVE)

    fun parse(html: String): List<ParsedBookmark> {
        val results = mutableListOf<ParsedBookmark>()
        val matcher = A_TAG_REGEX.matcher(html)

        while (matcher.find()) {
            val attributes = matcher.group(1) ?: continue
            val rawTitle = matcher.group(2) ?: ""

            val hrefMatcher = HREF_REGEX.matcher(attributes)
            if (!hrefMatcher.find()) continue
            // Browsers write the address HTML-escaped too: a query's & is &amp;
            val url = hrefMatcher.group(1)?.let(::decodeEntities)?.trim() ?: continue

            // Ignore empty URLs and internal browser pseudo-URLs
            if (url.isEmpty() ||
                url.startsWith("javascript:", ignoreCase = true) ||
                url.startsWith("place:", ignoreCase = true) ||
                url.startsWith("data:", ignoreCase = true)
            ) {
                continue
            }

            val addDateMatcher = ADD_DATE_REGEX.matcher(attributes)
            val addDateSeconds = if (addDateMatcher.find()) {
                addDateMatcher.group(1)?.toLongOrNull()
            } else null

            // Netscape bookmarks write ADD_DATE in seconds since epoch; convert to milliseconds
            val addDateMillis = addDateSeconds?.let { if (it > 0) it * 1000L else null }

            val cleanTitle = unescapeHtml(rawTitle).trim().takeIf { it.isNotEmpty() }

            results.add(
                ParsedBookmark(
                    url = url,
                    title = cleanTitle,
                    addDateMillis = addDateMillis,
                )
            )
        }

        return results
    }

    fun parse(inputStream: InputStream): List<ParsedBookmark> {
        val html = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parse(html)
    }

    /** A title as the browser showed it: inner tags stripped, entities decoded, whitespace collapsed. */
    fun unescapeHtml(input: String): String =
        decodeEntities(input.replace(Regex("<[^>]+>"), "")).replace(Regex("\\s+"), " ")

    /**
     * Decodes HTML character references in one pass, so "&amp;lt;" stays "&lt;"
     * rather than becoming "<", and a numeric reference beyond the basic plane
     * (an emoji) stays whole. Names it does not know are left as written.
     */
    fun decodeEntities(input: String): String =
        ENTITY_REGEX.replace(input) { match ->
            val body = match.groupValues[1]
            val codePoint = when {
                body.startsWith("#x", ignoreCase = true) -> body.substring(2).toIntOrNull(16)
                body.startsWith("#") -> body.substring(1).toIntOrNull()
                else -> null
            }
            when {
                codePoint != null && Character.isValidCodePoint(codePoint) -> String(Character.toChars(codePoint))
                codePoint != null -> match.value
                else -> NAMED_ENTITIES[body] ?: match.value
            }
        }

    private val ENTITY_REGEX = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]+);")

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "quot" to "\"", "apos" to "'", "lt" to "<", "gt" to ">", "nbsp" to " ",
        "copy" to "©", "reg" to "®", "trade" to "™", "mdash" to "—", "ndash" to "–", "hellip" to "…",
    )
}
