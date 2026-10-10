package com.lbc.breadcrumb.imports

import java.util.Locale

/**
 * The same page, however its address was written: the scheme, "www.", a
 * trailing slash and the #fragment are ignored, and the host lowercased. A
 * path's case and the query are kept: either can name a different page.
 * Pure, so JVM tests cover it.
 */
internal object UrlKey {

    private val URL = Regex("""(?i)\bhttps?://[^\s<>"]+|\bwww\.[^\s<>"]+""")
    private val SCHEME = Regex("(?i)^https?://")

    /** The key of one address, or null when it has no host. */
    fun of(url: String): String? {
        val address = SCHEME.replaceFirst(url.trim().substringBefore('#'), "")
        val hostEnd = address.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) address.length else it }
        val host = address.substring(0, hostEnd).lowercase(Locale.ROOT).removePrefix("www.")
        if (host.isEmpty()) return null
        val rest = address.substring(hostEnd)
        val path = rest.substringBefore('?').trimEnd('/')
        val query = rest.substringAfter('?', "")
        return host + path + if (query.isEmpty()) "" else "?$query"
    }

    /** The keys of every address in [text], without the punctuation a sentence puts after one. */
    fun allIn(text: String): Set<String> =
        URL.findAll(text).mapNotNull { of(it.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '\'')) }.toSet()
}
