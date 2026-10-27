package com.lbc.breadcrumb.ui.home

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import java.util.Locale

/**
 * Pure headline extraction for memories when the server summary is absent (L5).
 *
 * Rules:
 * - LINK -> page title
 * - PDF -> PDF title, otherwise first real line
 * - SCREENSHOT (IMAGE) -> first real OCR line from extractedText, skipping chrome
 * - NOTE (TEXT) -> first line
 * - Capped using the same first-line rules as ResultText (at 90 chars cut at a word with "…").
 */
object Headline {

    private val CLOCK_TIME_REGEX = Regex("""^(?:\d{1,2}[:.]\d{2}(?:\s*(?:am|pm|AM|PM))?)$""")
    private val BATTERY_REGEX = Regex("""^\d{1,3}\s*%$""")

    private val NETWORK_TOKENS = setOf(
        "kb/s", "mb/s", "gb/s", "b/s", "kbs", "mbs",
        "lte", "volte", "4g", "5g", "3g", "2g", "e", "h", "h+", "5g+", "lte+",
        "wifi", "wi-fi",
    )

    private val CHROME_PHRASES = setOf(
        "inbox", "to me", "from me", "me",
        "all inboxes", "primary", "promotions", "social", "updates", "forums",
        "starred", "snoozed", "important", "sent", "draft", "drafts", "outbox",
        "spam", "trash", "bin", "archive", "unread",
        "search", "search in mail", "search mail", "search in chat",
        "back", "cancel", "done", "edit", "close", "save", "menu", "more",
        "settings", "details", "notifications",
        "chats", "status", "calls", "channels", "communities",
        "home", "explore", "reels", "profile", "messages", "direct",
        "followers", "following", "posts",
        "today", "yesterday",
        "reply", "reply all", "forward",
    )

    fun of(memory: Memory): String? {
        val candidate = when (memory.type) {
            MemoryType.LINK -> {
                memory.title?.takeIf { it.isNotBlank() }
            }
            MemoryType.PDF -> {
                memory.title?.takeIf { it.isNotBlank() }
                    ?: firstRealLine(memory.extractedText ?: memory.rawText)
            }
            MemoryType.IMAGE -> {
                firstRealOcrLine(memory.extractedText)
                    ?: memory.title?.takeIf { it.isNotBlank() }
                    ?: memory.rawText?.let { ResultText.firstLine(it) }
            }
            MemoryType.TEXT -> {
                memory.rawText?.let { ResultText.firstLine(it) }
                    ?: memory.title?.takeIf { it.isNotBlank() }
            }
            MemoryType.AUDIO -> {
                memory.title?.takeIf { it.isNotBlank() }
                    ?: memory.extractedText?.let { ResultText.firstLine(it) }
            }
        }
        return candidate?.let { ResultText.firstLine(it) }
    }

    /**
     * Finds the first real OCR line from extracted text, skipping screenshot
     * chrome such as clock times, network lines, battery percentages, lone
     * chrome words (e.g. "Inbox", "to me"), and lines mostly made of symbols.
     */
    fun firstRealOcrLine(extractedText: String?): String? {
        if (extractedText.isNullOrBlank()) return null
        return extractedText.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !isScreenshotChrome(it) }
    }

    /**
     * Finds the first real line of document text, skipping blank lines and
     * lines mostly made of symbols or lone punctuation.
     */
    fun firstRealLine(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return text.lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                line.isNotBlank() && line.count { it.isLetterOrDigit() } >= 2 && !isMostlySymbols(line)
            }
    }

    fun isScreenshotChrome(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return true

        val letterOrDigitCount = trimmed.count { it.isLetterOrDigit() }
        if (letterOrDigitCount < 2) return true

        if (isMostlySymbols(trimmed)) return true
        if (CLOCK_TIME_REGEX.matches(trimmed)) return true
        if (BATTERY_REGEX.matches(trimmed)) return true
        if (isNetworkLine(trimmed)) return true
        if (isCombinedStatusBarLine(trimmed)) return true
        if (isLoneChromeWord(trimmed)) return true

        return false
    }

    private fun isMostlySymbols(line: String): Boolean {
        val letterOrDigitCount = line.count { it.isLetterOrDigit() }
        val nonWhitespaceCount = line.count { !it.isWhitespace() }
        return nonWhitespaceCount > 0 && (letterOrDigitCount.toDouble() / nonWhitespaceCount) < 0.5
    }

    private fun isNetworkLine(line: String): Boolean {
        val tokens = line.split(Regex("""[\s/|•\-]+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        return tokens.all { token ->
            val lower = token.lowercase(Locale.ROOT)
            lower in NETWORK_TOKENS || lower.matches(Regex("""^\d+(?:\.\d+)?$"""))
        }
    }

    private fun isCombinedStatusBarLine(line: String): Boolean {
        val tokens = line.split(Regex("""[\s|•\-]+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        var hasStatusIndicator = false
        for (token in tokens) {
            val lower = token.lowercase(Locale.ROOT)
            when {
                CLOCK_TIME_REGEX.matches(token) -> hasStatusIndicator = true
                BATTERY_REGEX.matches(token) -> hasStatusIndicator = true
                lower in NETWORK_TOKENS -> hasStatusIndicator = true
                lower.matches(Regex("""^\d+(?:\.\d+)?$""")) -> Unit
                lower == "am" || lower == "pm" -> Unit
                else -> return false
            }
        }
        return hasStatusIndicator
    }

    private fun isLoneChromeWord(line: String): Boolean {
        val lower = line.lowercase(Locale.ROOT)
        return lower in CHROME_PHRASES
    }
}
