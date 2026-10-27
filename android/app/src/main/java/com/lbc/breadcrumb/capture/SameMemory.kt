package com.lbc.breadcrumb.capture

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.ocr.LinkRules
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/**
 * Whether what is being saved is kept already (roadmap 1): a link is the same
 * link whatever tracking a share added to it; text is the same words. Pure, so
 * JVM tests cover it. A file is the same bytes, which takes the files
 * themselves ([CaptureStorage.keptFile]).
 */
object SameMemory {

    /** The kept memory [memory] repeats, the newest such among [kept]; null when it is new. */
    fun keptLike(memory: Memory, kept: List<Memory>): Memory? {
        val key = keyOf(memory) ?: return null
        return kept.filter { it.id != memory.id && it.type == memory.type && keyOf(it) == key }
            .maxByOrNull { it.capturedAt }
    }

    private fun keyOf(memory: Memory): String? {
        val text = memory.rawText?.takeIf { it.isNotBlank() } ?: return null
        return when (memory.type) {
            MemoryType.LINK -> TextRules.firstUrl(text)?.let(::linkKey)
            MemoryType.TEXT -> textKey(text)
            else -> null
        }
    }

    /** Text compared as words: case and spacing aside. */
    fun textKey(text: String): String = text.trim().replace(Regex("""\s+"""), " ").lowercase(Locale.ROOT)

    /**
     * A link compared as the page it opens: no scheme, no "www." or "m.", no
     * fragment, no trailing slash, no tracking parameters (utm_…, Instagram's
     * igsh, YouTube's si), the rest in order; youtu.be/ID is youtube.com/watch?v=ID.
     */
    fun linkKey(url: String): String? {
        val parsed = LinkRules.normalizeUrl(url)?.toHttpUrlOrNull() ?: return null
        var host = parsed.host.lowercase(Locale.ROOT)
        for (prefix in listOf("www.", "m.", "mobile.")) host = host.removePrefix(prefix)
        var path = parsed.encodedPath.trimEnd('/')
        val query = (0 until parsed.querySize)
            .map { parsed.queryParameterName(it) to parsed.queryParameterValue(it).orEmpty() }
            .filterNot { (name, _) -> isTracking(name, host) }
            .toMutableList()
        if (host == "youtu.be" && path.length > 1) {
            query += "v" to path.removePrefix("/")
            host = "youtube.com"
            path = "/watch"
        }
        val kept = query.sortedWith(compareBy({ it.first }, { it.second })).joinToString("&") { "${it.first}=${it.second}" }
        return host + path + if (kept.isEmpty()) "" else "?$kept"
    }

    private fun isTracking(name: String, host: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        if (lower.startsWith("utm_") || lower in TRACKING) return true
        // X's share links add s= and t=; elsewhere they can mean something.
        return (host == "x.com" || host == "twitter.com") && (lower == "s" || lower == "t")
    }

    private val TRACKING = setOf(
        "fbclid", "gclid", "dclid", "msclkid", "igsh", "igshid", "obrf", "si", "feature", "pp",
        "mc_cid", "mc_eid", "ref_src", "ref_url", "_ga", "share_id", "trk", "trackingid", "refid", "lipi",
    )
}
