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
            val url = hrefMatcher.group(1)?.trim() ?: continue

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

    fun unescapeHtml(input: String): String {
        // Strip any inner HTML formatting tags (e.g. <b>, <span>)
        val stripped = input.replace(Regex("<[^>]+>"), "")
        return stripped
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&copy;", "©")
            .replace("&reg;", "®")
            .replace("&trade;", "™")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace(Regex("&#(\\d+);")) { matchResult ->
                val code = matchResult.groupValues[1].toIntOrNull()
                if (code != null) code.toChar().toString() else matchResult.value
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { matchResult ->
                val code = matchResult.groupValues[1].toIntOrNull(16)
                if (code != null) code.toChar().toString() else matchResult.value
            }
            .replace(Regex("\\s+"), " ")
    }
}
