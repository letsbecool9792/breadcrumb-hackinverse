package com.lbc.breadcrumb.voice

import java.io.File

/** Turns a voice note into its words, on the phone. */
fun interface Transcriber {
    suspend fun transcribe(file: File): Heard
}

/** What listening to a voice note came to. */
sealed interface Heard {
    /** Its words; empty when nothing was said that could be made out. */
    data class Words(val text: String) : Heard

    /**
     * Not now: the recognizer is busy, its language is still downloading, or
     * Breadcrumb isn't on screen (the phone lends its recognizer only to the
     * app in front). The note waits for the next try.
     */
    data class NotNow(val why: String) : Heard

    /** This phone can't: Android before 13, or no recognizer that works offline. */
    data object Never : Heard
}
