package com.lbc.breadcrumb.capture

/** What a tap on the tile saves from the clipboard. Pure, so JVM tests cover it. */
internal object ClipboardRules {

    sealed interface Decision {
        data object Empty : Decision
        data object Sensitive : Decision
        data class Text(val text: String) : Decision
        data object Files : Decision
    }

    /**
     * A clip marked sensitive (a password, a one-time code) is never saved,
     * whatever else it holds. Text wins over files, since a copied link or
     * paragraph is what people mean; several texts join, one per line. Files
     * only when there is no text; else nothing.
     */
    fun decide(sensitive: Boolean, texts: List<String>, fileCount: Int): Decision {
        val words = texts.map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            sensitive -> Decision.Sensitive
            words.isNotEmpty() -> Decision.Text(words.joinToString("\n"))
            fileCount > 0 -> Decision.Files
            else -> Decision.Empty
        }
    }
}
