package com.lbc.breadcrumb.voice

import com.lbc.breadcrumb.data.Memory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File

/**
 * Hears the voice notes not heard yet, newest first, and writes what was said
 * as their extractedText: word search finds them by it, and the phone embeds
 * it. Runs in [com.lbc.breadcrumb.BreadcrumbApp.onCreate], in every process.
 *
 * It watches Room for notes not heard yet, and looks again at every [wakes] (an
 * activity of the app starting): a note that had to wait (the recognizer busy,
 * its language downloading, the app not on screen) is tried again then, and
 * waits for nothing else. A note this phone can never hear, or whose file is
 * gone, is marked heard with no words: it is embedded by its note, and stops
 * saying it is being listened to.
 */
class ListenQueue(
    private val unheard: Flow<List<String>>,
    private val wakes: Flow<*>,
    private val newest: suspend (limit: Int) -> List<Memory>,
    private val fileOf: (Memory) -> File?,
    /** Writes the words where the note is still not heard; the rows written. */
    private val write: suspend (id: String, words: String) -> Int,
    private val transcriber: Transcriber,
    private val scheduleWork: () -> Unit,
) {

    /** Listens for as long as the scope it runs in; a failure ends it quietly rather than the process. */
    suspend fun run() {
        try {
            combine(unheard.distinctUntilChanged(), wakes) { ids, _ -> ids }
                .conflate()
                .collect { ids -> if (ids.isNotEmpty()) pass() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Voice notes wait for the next start; nothing else does.
        }
    }

    /** Every note not heard yet, newest first; one that has to wait is left for the next pass. */
    internal suspend fun pass() {
        val waiting = mutableSetOf<String>()
        while (true) {
            val memory = newest(waiting.size + 1).firstOrNull { it.id !in waiting } ?: return
            val file = fileOf(memory)
            val heard = if (file == null) {
                Heard.Words("")
            } else {
                try {
                    transcriber.transcribe(file)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Heard.NotNow(e.toString())
                }
            }
            when (heard) {
                is Heard.Words -> if (write(memory.id, heard.text) > 0) scheduleWork()
                Heard.Never -> if (write(memory.id, "") > 0) scheduleWork()
                is Heard.NotNow -> waiting += memory.id
            }
        }
    }
}
