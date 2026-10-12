package com.lbc.breadcrumb.sync

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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

    /**
     * A "forget everything" the server hasn't confirmed yet: set when "Gemini
     * reads my memories" is turned off, and cleared by the upload pass once the
     * server answers 200. Turning reading back on doesn't cancel it.
     */
    var forgetPending: Boolean
        get() = prefs.getBoolean(KEY_FORGET_PENDING, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FORGET_PENDING, value).apply()
        }

    /** Fires on every change to these settings, from any thread: the upload pass clears [forgetPending]. */
    fun changes(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        const val PREFS_NAME = "breadcrumb_sync_settings"
        const val KEY_GEMINI_READS_MEMORIES = "gemini_reads_memories"
        const val KEY_CLOUD_PARSES = "cloud_parses"
        const val KEY_FORGET_PENDING = "forget_pending"
    }
}
