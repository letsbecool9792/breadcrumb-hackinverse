package com.lbc.breadcrumb.sync

import android.content.Context
import android.content.SharedPreferences

/**
 * L4 setting: "Gemini reads my memories", off by default in SharedPreferences.
 * While off, no memories and no pictures leave the phone; deletes still go.
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

    companion object {
        const val PREFS_NAME = "breadcrumb_sync_settings"
        const val KEY_GEMINI_READS_MEMORIES = "gemini_reads_memories"
    }
}
