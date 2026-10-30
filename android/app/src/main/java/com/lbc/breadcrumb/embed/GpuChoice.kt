package com.lbc.breadcrumb.embed

import android.content.Context

/**
 * Whether this phone's GPU runs the model's vision encoder, for pictures
 * (roadmap 6): untried until the first picture, then known. A start that
 * throws, or that never ends (a crash in a GPU driver takes the process with
 * it, past any catch), leaves pictures on the CPU for good on this phone: a
 * start is marked before it begins, and only one that finishes clears the mark.
 * Pure over [read] and [write], which must reach the disk before returning.
 */
class GpuChoice(private val read: () -> String?, private val write: (String) -> Unit) {

    /** Whether to start the GPU now. A start marked and never ended failed. */
    fun allowed(): Boolean = when (read()) {
        null, WORKS -> true
        TRYING -> {
            write(FAILS)
            false
        }
        else -> false
    }

    fun starting() = write(TRYING)

    fun worked() = write(WORKS)

    fun failed() = write(FAILS)

    companion object {
        private const val TRYING = "trying"
        private const val WORKS = "works"
        private const val FAILS = "fails"

        /** Kept in SharedPreferences, written with commit(): the mark must be on disk before a start that may crash. */
        fun of(context: Context): GpuChoice {
            val prefs = context.getSharedPreferences("embedder", Context.MODE_PRIVATE)
            return GpuChoice(
                read = { prefs.getString("gpu", null) },
                write = { prefs.edit().putString("gpu", it).commit() },
            )
        }
    }
}
