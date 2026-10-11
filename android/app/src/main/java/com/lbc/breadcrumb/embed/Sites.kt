package com.lbc.breadcrumb.embed

import com.lbc.breadcrumb.data.Memory
import java.net.URI

/**
 * A link's site as a person would name it, as the server's `sites.ts` does:
 * "youtu.be" is YouTube, "www.linkedin.com" is LinkedIn, anything else its
 * registrable domain's first label, capitalised. Pure.
 */
object Sites {

    private val NAMED = mapOf(
        "instagram.com" to "Instagram", "instagr.am" to "Instagram",
        "youtube.com" to "YouTube", "youtu.be" to "YouTube",
        "twitter.com" to "X", "x.com" to "X", "t.co" to "X",
        "linkedin.com" to "LinkedIn", "lnkd.in" to "LinkedIn",
        "facebook.com" to "Facebook", "fb.com" to "Facebook", "fb.me" to "Facebook",
        "whatsapp.com" to "WhatsApp", "wa.me" to "WhatsApp",
        "reddit.com" to "Reddit", "redd.it" to "Reddit",
        "github.com" to "GitHub",
        "t.me" to "Telegram", "telegram.me" to "Telegram",
        "amzn.to" to "Amazon", "amzn.in" to "Amazon",
        "pin.it" to "Pinterest",
        "tiktok.com" to "TikTok",
    )

    private val SECOND_LEVELS = setOf("co", "com", "net", "org", "gov", "edu", "ac", "nic", "res", "gen", "firm", "ind", "ltd", "plc")

    private val URL = Regex("""(?:https?://|\bwww\.)[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    /** The most sites the parse is offered, as the server's list. */
    private const val MAX_SITES = 40

    fun registrableDomain(host: String): String {
        val labels = host.lowercase().trimEnd('.').split('.').filter { it.isNotEmpty() }
        if (labels.size <= 2) return labels.joinToString(".")
        val top = labels.last()
        val second = labels[labels.size - 2]
        val take = if (top.length == 2 && second in SECOND_LEVELS) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }

    fun name(host: String): String? {
        val domain = registrableDomain(host)
        if ('.' !in domain || domain.all { it.isDigit() || it == '.' }) return null
        NAMED[domain]?.let { return it }
        val first = domain.substringBefore('.')
        return first.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() }
    }

    /** The sites of every link in [text]. */
    fun of(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return URL.findAll(text).mapNotNull { match ->
            val url = match.value.let { if (it.startsWith("www.", ignoreCase = true)) "https://$it" else it }
            runCatching { URI(url).host }.getOrNull()?.let(::name)
        }.distinct().toList()
    }

    /**
     * What a search phrase may name as its source: every app something was
     * shared from, and the most-saved sites. The phone sends these with the
     * phrase, since the server may hold no memories.
     */
    fun sourcesOf(memories: List<Memory>): List<String> {
        val apps = memories.mapNotNull { it.sourceAppLabel?.takeIf(String::isNotBlank) }
        val sites = memories.flatMap { of(it.rawText) }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(MAX_SITES).map { it.key }
        return (apps + sites).distinct().sorted()
    }
}
