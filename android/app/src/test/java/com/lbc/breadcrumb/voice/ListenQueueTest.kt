package com.lbc.breadcrumb.voice

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ListenQueueTest {

    private val notes = mutableListOf<Memory>()
    private var scheduled = 0
    private val asked = mutableListOf<String>()

    private fun note(id: String, at: Long, file: Boolean = true) =
        Memory(id = id, type = MemoryType.AUDIO, capturedAt = at, localUri = if (file) "file:///$id.m4a" else null)
            .also { notes += it }

    private fun queue(hear: (String) -> Heard) = ListenQueue(
        unheard = MutableStateFlow(emptyList()),
        wakes = MutableStateFlow(0),
        newest = { limit -> notes.filter { it.extractedText == null }.sortedByDescending { it.capturedAt }.take(limit) },
        fileOf = { memory -> memory.localUri?.let { File(it.removePrefix("file:///")) } },
        write = { id, words ->
            val i = notes.indexOfFirst { it.id == id && it.extractedText == null }
            if (i < 0) 0 else 1.also { notes[i] = notes[i].copy(extractedText = words) }
        },
        transcriber = { file -> asked += file.nameWithoutExtension; hear(file.nameWithoutExtension) },
        scheduleWork = { scheduled++ },
    )

    private fun words(id: String) = notes.single { it.id == id }.extractedText

    @Test
    fun `notes are heard newest first, and what follows a read is scheduled`() = runTest {
        note("old", 1)
        note("new", 2)

        queue { Heard.Words("words of $it") }.pass()

        assertEquals(listOf("new", "old"), asked)
        assertEquals("words of old", words("old"))
        assertEquals(2, scheduled)
    }

    @Test
    fun `a note that has to wait stays unheard, and the rest are still heard`() = runTest {
        note("busy", 2)
        note("fine", 1)

        queue { if (it == "busy") Heard.NotNow("busy") else Heard.Words("fine words") }.pass()

        assertEquals(null, words("busy"))
        assertEquals("fine words", words("fine"))
        assertEquals(listOf("busy", "fine"), asked)
    }

    @Test
    fun `a note never to be heard, or without its file, is heard with no words`() = runTest {
        note("never", 2)
        note("gone", 1, file = false)

        queue { Heard.Never }.pass()

        assertEquals("", words("never"))
        assertEquals("", words("gone"))
        assertEquals(listOf("never"), asked)
    }

    @Test
    fun `a recognizer that throws only puts that note off`() = runTest {
        note("throws", 2)
        note("fine", 1)

        queue { if (it == "throws") throw IllegalStateException("boom") else Heard.Words("ok") }.pass()

        assertEquals(null, words("throws"))
        assertEquals("ok", words("fine"))
    }
}
