package com.lbc.breadcrumb.ui.capture

import com.lbc.breadcrumb.capture.TextRules
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import java.util.Locale

/** What the capture sheet says about what was saved. Pure, so JVM tests cover it. */
internal object SheetText {

    /**
     * The line under "Saved": what was saved, and where from only when every
     * item agrees ("image · from whatsapp", "3 images", "2 items"). Lowercase,
     * as the app's chrome is.
     */
    fun kindLine(memories: List<Memory>): String {
        if (memories.isEmpty()) return ""
        val kinds = memories.map { it.type }.distinct()
        val kind = when {
            memories.size == 1 -> word(kinds.single())
            kinds.size == 1 -> "${memories.size} ${word(kinds.single())}s"
            else -> "${memories.size} items"
        }
        val source = memories
            .map { it.sourceAppLabel?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .singleOrNull()
        return listOfNotNull(kind, source?.let { "from ${it.lowercase(Locale.ROOT)}" }).joinToString(" · ")
    }

    private fun word(type: MemoryType): String = type.name.lowercase(Locale.ROOT)

    data class LinkCard(val host: String, val headline: String?, val url: String)

    /**
     * A link's card: the host of the first URL anywhere in [text] (shared text
     * often wraps the link in words), the page title as headline when there is
     * one, and that URL beneath. Null when the text holds no URL.
     */
    fun linkCard(text: String?, title: String?): LinkCard? {
        val url = text?.let(TextRules::firstUrl) ?: return null
        val host = url.substringAfter("://", url)
            .takeWhile { it != '/' && it != '?' && it != '#' && it != ':' }
            .lowercase(Locale.ROOT)
            .removePrefix("www.")
        if (host.isEmpty()) return null
        return LinkCard(host, title?.trim()?.takeIf { it.isNotEmpty() }, url)
    }
}
