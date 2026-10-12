package com.lbc.breadcrumb.ocr

/**
 * Result of reading an HTML page's `<head>`.
 */
data class LinkReadResult(
    /** Extracted `<title>`, or null if missing/empty. */
    val title: String?,
    /** Extracted meta description, or empty string if missing/unreadable. */
    val description: String,
    /** The page's preview picture (og:image, or a video's thumbnail), when it has one. */
    val imageUrl: String? = null,
) {
    companion object {
        val EMPTY = LinkReadResult(title = null, description = "")
    }
}

/**
 * Reads an HTML page's head content from a web URL.
 */
interface LinkReader {
    /**
     * Reads the page at [url] and extracts its title and description.
     * Returns [LinkReadResult.EMPTY] if the page cannot be read, rejected, or on error.
     */
    suspend fun readLink(url: String): LinkReadResult
}
