package com.lbc.breadcrumb.data

/**
 * Sanitises and formats user input for full-text search against [MemoryFts].
 *
 * In FTS4, raw user input containing `"`, `-`, `OR` or `column:` can cause
 * SQLite syntax errors or change the query semantics.
 *
 * Words are split as unicode61 does: letters and digits make a word, anything
 * else separates. Every word becomes a quoted prefix term, and all must match:
 * `qualcomm intern` -> `"qualcomm*" "intern*"`
 *
 * This ensures "intern" finds "internship" while it is still being typed.
 */
object FtsQuery {

    /**
     * Turns raw user search input into a safe FTS4 MATCH expression.
     *
     * Returns null if [raw] contains no letters or digits.
     */
    fun matchExpression(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        val words = mutableListOf<String>()
        val current = StringBuilder()

        var i = 0
        while (i < raw.length) {
            val codePoint = raw.codePointAt(i)
            if (Character.isLetterOrDigit(codePoint)) {
                current.appendCodePoint(codePoint)
            } else if (current.isNotEmpty()) {
                words.add(current.toString().lowercase())
                current.setLength(0)
            }
            i += Character.charCount(codePoint)
        }
        if (current.isNotEmpty()) {
            words.add(current.toString().lowercase())
        }

        if (words.isEmpty()) return null
        return words.joinToString(" ") { "\"$it*\"" }
    }
}
