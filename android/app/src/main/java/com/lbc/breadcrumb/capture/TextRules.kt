package com.lbc.breadcrumb.capture

import com.lbc.breadcrumb.data.MemoryType

object TextRules {
    private val URL_REGEX = """(?i)\bhttps?://[^\s<>"]+|\bwww\.[^\s<>"]+""".toRegex()

    fun parseText(text: String): Pair<MemoryType, Boolean> {
        val hasLink = URL_REGEX.containsMatchIn(text)
        val type = if (hasLink) MemoryType.LINK else MemoryType.TEXT
        return Pair(type, hasLink)
    }

    /** "Photo from Priya", "Document from …": what a chat app writes, not a title. */
    fun isGenericSubject(subject: String): Boolean = GENERIC_SUBJECT.containsMatchIn(subject.trim())

    /**
     * A shared text's title: its subject, unless that is generic or merely
     * repeats the text (some apps send the text as the subject as well).
     */
    fun titleFor(subject: String?, text: String): String? {
        val trimmed = subject?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (isGenericSubject(trimmed) || trimmed == text.trim()) return null
        return trimmed
    }

    private val GENERIC_SUBJECT = Regex("^(photo|image|video|document|file)s? from\\b", RegexOption.IGNORE_CASE)
}
