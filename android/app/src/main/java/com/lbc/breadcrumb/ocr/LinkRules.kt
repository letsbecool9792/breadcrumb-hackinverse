package com.lbc.breadcrumb.ocr

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * URL normalization, host security validation, and HTML head metadata extraction
 * for link reading (R4).
 */
object LinkRules {

    private val IPV4_REGEX = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")

    /**
     * Normalizes a URL:
     * - Trims whitespace
     * - Upgrades "http://" to "https://"
     * - Prepends "https://" to URLs starting with "www." or bare domains
     * - Rejects non-http(s) schemes (ftp, javascript, file, etc.)
     */
    fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring(7)
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("www.", ignoreCase = true) -> "https://$trimmed"
            trimmed.contains("://") -> return null // unsupported scheme
            else -> "https://$trimmed"
        }

        return try {
            val parsed = withScheme.toHttpUrlOrNull() ?: return null
            parsed.toString()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks whether a normalized URL is safe to fetch:
     * - Never localhost URLs (e.g. localhost, *.localhost, 127.0.0.1, ::1)
     * - Never bare IP addresses (IPv4 or IPv6 literals)
     * - Never private/internal domain names (.local, .internal, .lan)
     */
    fun isAllowedUrl(url: String): Boolean {
        val parsed = try {
            url.toHttpUrlOrNull() ?: return false
        } catch (_: Exception) {
            return false
        }

        val host = parsed.host.lowercase()

        // Localhost checks
        if (host == "localhost" ||
            host.endsWith(".localhost") ||
            host == "localhost.localdomain" ||
            host == "broadcasthost" ||
            host.endsWith(".local") ||
            host.endsWith(".internal") ||
            host.endsWith(".lan") ||
            host == "127.0.0.1" ||
            host.startsWith("127.") ||
            host == "::1" ||
            host == "[::1]"
        ) {
            return false
        }

        // Bare IPv4 rejection (including private and public bare IPs)
        if (IPV4_REGEX.matches(host)) {
            return false
        }

        // Bare IPv6 rejection
        if (host.contains(':') || host.startsWith('[') || host.endsWith(']')) {
            return false
        }

        return true
    }

    /**
     * Extracts content up to the closing `</head>` tag, or the whole HTML if no `</head>` is found.
     */
    fun extractHeadContent(html: String): String {
        val headCloseIndex = html.indexOf("</head>", ignoreCase = true)
        return if (headCloseIndex != -1) {
            html.substring(0, headCloseIndex + 7)
        } else {
            html
        }
    }

    /**
     * Extracts the page title from `<title>...</title>`.
     * Strips nested tags, decodes HTML entities, and collapses whitespace.
     * Returns null if no title or empty.
     */
    fun extractTitle(htmlHead: String): String? {
        val match = Regex("""(?is)<title[^>]*>(.*?)</title>""").find(htmlHead) ?: return null
        val raw = match.groupValues[1]
        val clean = raw
            .replace(Regex("<[^>]+>"), "")
            .let { decodeHtmlEntities(it) }
            .replace(Regex("""\s+"""), " ")
            .trim()
        return clean.ifEmpty { null }
    }

    /**
     * Extracts the page description from `<meta>` tags:
     * 1. `<meta name="description" content="...">`
     * 2. `<meta property="og:description" content="...">`
     * 3. `<meta name="twitter:description" content="...">`
     *
     * Returns an empty string if no description found.
     */
    fun extractDescription(htmlHead: String): String {
        var standardDesc: String? = null
        var ogDesc: String? = null
        var twitterDesc: String? = null

        val metaRegex = Regex("""(?is)<meta\s+([^>]*?)>""")
        for (match in metaRegex.findAll(htmlHead)) {
            val attrs = match.groupValues[1]
            val name = getAttribute(attrs, "name")?.lowercase()
            val property = getAttribute(attrs, "property")?.lowercase()
            val content = getAttribute(attrs, "content") ?: continue

            val cleanContent = content
                .replace(Regex("<[^>]+>"), "")
                .let { decodeHtmlEntities(it) }
                .replace(Regex("""\s+"""), " ")
                .trim()

            if (cleanContent.isEmpty()) continue

            if (name == "description" && standardDesc == null) {
                standardDesc = cleanContent
            } else if ((property == "og:description" || name == "og:description") && ogDesc == null) {
                ogDesc = cleanContent
            } else if ((name == "twitter:description" || property == "twitter:description") && twitterDesc == null) {
                twitterDesc = cleanContent
            }
        }

        val chosen = standardDesc ?: ogDesc ?: twitterDesc
        return chosen ?: ""
    }

    /**
     * Extracts an attribute value from an HTML tag attribute string.
     * Handles single quotes, double quotes, and unquoted values.
     */
    private fun getAttribute(attrs: String, attrName: String): String? {
        val regex = Regex("""(?i)\b$attrName\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
        val match = regex.find(attrs) ?: return null
        val v1 = match.groupValues[1]
        if (v1.isNotEmpty()) return v1
        val v2 = match.groupValues[2]
        if (v2.isNotEmpty()) return v2
        val v3 = match.groupValues[3]
        if (v3.isNotEmpty()) return v3
        return null
    }

    /**
     * Decodes common named and numeric HTML entities.
     */
    fun decodeHtmlEntities(text: String): String {
        var result = text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace("&copy;", "©")
            .replace("&reg;", "®")
            .replace("&trade;", "™")

        // Decimal numeric entities: &#([0-9]+);
        result = Regex("""&#([0-9]+);""").replace(result) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null && code in 1..0x10ffff) {
                String(Character.toChars(code))
            } else {
                match.value
            }
        }

        // Hex numeric entities: &#x([0-9a-fA-F]+);
        result = Regex("""&#x([0-9a-fA-F]+);""", RegexOption.IGNORE_CASE).replace(result) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null && code in 1..0x10ffff) {
                String(Character.toChars(code))
            } else {
                match.value
            }
        }

        return result
    }

    /**
     * The page's preview picture: og:image (or its secure_url), else
     * twitter:image, resolved against [pageUrl]. Null when it has none.
     */
    fun extractImage(htmlHead: String, pageUrl: String): String? {
        var og: String? = null
        var twitter: String? = null
        val metaRegex = Regex("""(?is)<meta\s+([^>]*?)>""")
        for (match in metaRegex.findAll(htmlHead)) {
            val attrs = match.groupValues[1]
            val key = (getAttribute(attrs, "property") ?: getAttribute(attrs, "name"))?.lowercase() ?: continue
            val content = getAttribute(attrs, "content")?.let { decodeHtmlEntities(it).trim() }?.takeIf { it.isNotEmpty() } ?: continue
            when (key) {
                "og:image", "og:image:secure_url", "og:image:url" -> if (og == null) og = content
                "twitter:image", "twitter:image:src" -> if (twitter == null) twitter = content
            }
        }
        val found = og ?: twitter ?: return null
        return runCatching { java.net.URI(pageUrl).resolve(found).toString() }.getOrNull()
    }
}
