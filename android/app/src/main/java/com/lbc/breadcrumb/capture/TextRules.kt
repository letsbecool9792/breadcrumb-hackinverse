package com.lbc.breadcrumb.capture

import com.lbc.breadcrumb.data.MemoryType

object TextRules {
    private val URL_REGEX = """(?i)\bhttps?://[^\s<>"]+|\bwww\.[^\s<>"]+""".toRegex()
    
    fun parseText(text: String): Pair<MemoryType, Boolean> {
        val hasLink = URL_REGEX.containsMatchIn(text)
        val type = if (hasLink) MemoryType.LINK else MemoryType.TEXT
        return Pair(type, hasLink)
    }

    fun cleanSubject(subject: String?): String? {
        if (subject == null) return null
        val trimmed = subject.trim()
        if (trimmed.isEmpty()) return null
        
        val lower = trimmed.lowercase()
        if (lower.startsWith("photo from ") || lower.startsWith("video from ") || lower.startsWith("image from ")) {
            return null
        }
        
        return trimmed
    }
}
