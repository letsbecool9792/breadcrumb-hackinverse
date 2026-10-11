package com.lbc.breadcrumb.sync

import android.content.Context
import android.content.SharedPreferences

/**
 * What may leave the phone, switched in the privacy sheet (local first):
 * "Gemini reads my memories", off by default, and "Understand searches in the
 * cloud", on by default. With both off the app makes no call to the server.
 */
class SyncSettings(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    var geminiReadsMemories: Boolean
        get() = prefs.getBoolean(KEY_GEMINI_READS_MEMORIES, false)
        set(value) {
            prefs.edit().putBoolean(KEY_GEMINI_READS_MEMORIES, value).apply()
        }

    /** Whether a search phrase goes to the server to be parsed into filters. Off, search runs without them. */
    var cloudParses: Boolean
        get() = prefs.getBoolean(KEY_CLOUD_PARSES, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CLOUD_PARSES, value).apply()
        }

    companion object {
        const val PREFS_NAME = "breadcrumb_sync_settings"
        const val KEY_GEMINI_READS_MEMORIES = "gemini_reads_memories"
        const val KEY_CLOUD_PARSES = "cloud_parses"
    }
}
