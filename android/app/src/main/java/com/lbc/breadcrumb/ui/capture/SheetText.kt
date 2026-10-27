package com.lbc.breadcrumb.ui.capture

import com.lbc.breadcrumb.capture.TextRules
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.ui.home.ResultText
import java.util.Locale

/** What the capture sheet says about what was saved. Pure, so JVM tests cover it. */
internal object SheetText {

    /**
     * The line under "Saved": what was saved, and where from only when every
     * item agrees ("image · from whatsapp", "3 images", "2 items"), and how
     * many of the share were kept already ("2 images · 1 kept already").
     * Lowercase, as the app's chrome is.
     */
    fun kindLine(memories: List<Memory>, keptAlready: Int = 0): String {
        if (memories.isEmpty()) return ""
        val source = memories
            .map { it.sourceAppLabel?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .singleOrNull()
        return listOfNotNull(
            kinds(memories),
            source?.let { "from ${it.lowercase(Locale.ROOT)}" },
            "$keptAlready kept already".takeIf { keptAlready > 0 },
        ).joinToString(" · ")
    }

    /**
     * The line under "Kept already": what it is, and when it was kept
     * ("link · kept 3d ago", "image · kept just now", "2 images · kept before").
     */
    fun keptLine(memories: List<Memory>, now: Long): String {
        if (memories.isEmpty()) return ""
        val single = memories.singleOrNull()
        val kept = when {
            single == null -> "kept before"
            else -> ResultText.age(single.capturedAt, now).let { if (it == "now") "kept just now" else "kept $it ago" }
        }
        return "${kinds(memories)} · $kept"
    }

    private fun kinds(memories: List<Memory>): String {
        val kinds = memories.map { it.type }.distinct()
        return when {
            memories.size == 1 -> word(kinds.single())
            kinds.size == 1 -> "${memories.size} ${word(kinds.single())}s"
            else -> "${memories.size} items"
        }
    }

    private fun word(type: MemoryType): String =
        if (type == MemoryType.AUDIO) "voice note" else type.name.lowercase(Locale.ROOT)

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
