package com.lbc.breadcrumb.ui.capture

import com.lbc.breadcrumb.capture.TextRules
import java.util.Locale

/** What the capture sheet says about what was saved. Pure, so JVM tests cover it. */
internal object SheetText {

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
