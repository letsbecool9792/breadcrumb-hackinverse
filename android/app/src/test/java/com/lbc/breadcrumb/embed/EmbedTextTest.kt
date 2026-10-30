package com.lbc.breadcrumb.embed

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbedTextTest {

    private val note = Memory(id = "n", type = MemoryType.TEXT, rawText = "Try the brewery burger in Indiranagar")
    private val screenshot = "12:20 / Congrats, you've been accepted to ETHGlobal Mumbai Hackathon! Hey Suparno, we reviewed your application"

    @Test
    fun `a note is embedded from its own words, with the document prompt`() {
        val want = EmbedText.want(note, hasPicture = false) as EmbedText.AsText
        assertEquals("title: none | text: Try the brewery burger in Indiranagar", want.prompt)
    }

    @Test
    fun `a search phrase gets the query prompt`() {
        assertEquals("task: search result | query: where priya said to eat", EmbedText.query("  where priya said to eat "))
    }

    @Test
    fun `the server's summary is never part of it`() {
        val summarised = note.copy(summary = "Restaurant recommendation")
        assertEquals(EmbedText.want(note, false)?.key, EmbedText.want(summarised, false)?.key)
    }

    @Test
    fun `a changed note changes the key, so it is embedded again`() {
        val noted = note.copy(note = "Priya said")
        assertNotEquals(EmbedText.want(note, false)?.key, EmbedText.want(noted, false)?.key)
    }

    @Test
    fun `a picture or pdf still being read waits`() {
        assertNull(EmbedText.want(Memory(type = MemoryType.IMAGE, extractedText = null), hasPicture = true))
        assertNull(EmbedText.want(Memory(type = MemoryType.PDF, extractedText = null), hasPicture = false))
    }

    @Test
    fun `nothing to embed is nothing`() {
        assertNull(EmbedText.want(Memory(type = MemoryType.TEXT, rawText = "  "), hasPicture = false))
    }

    @Test
    fun `a screenshot with words is embedded from its words`() {
        val memory = Memory(type = MemoryType.IMAGE, extractedText = screenshot)
        assertTrue(EmbedText.want(memory, hasPicture = true) is EmbedText.AsText)
    }

    @Test
    fun `a picture ocr could barely read is embedded as a picture, its few words beside it and standing in for it`() {
        val memory = Memory(type = MemoryType.IMAGE, extractedText = "20:45", note = "the beach")
        val want = EmbedText.want(memory, hasPicture = true) as EmbedText.AsPicture
        assertEquals("title: none | text: 20:45\nthe beach", want.prompt)
        assertEquals(want.prompt, want.words?.prompt)
        assertNotEquals(want.key, want.words?.key)
    }

    @Test
    fun `a picture with no words at all still has its picture`() {
        val want = EmbedText.want(Memory(type = MemoryType.IMAGE, extractedText = ""), hasPicture = true) as EmbedText.AsPicture
        assertNull(want.prompt)
        assertNull(want.words)
    }

    @Test
    fun `long text is cut to what the model reads`() {
        val memory = Memory(type = MemoryType.PDF, extractedText = "x".repeat(10_000))
        assertEquals(EmbedText.MAX_CHARS, EmbedText.text(memory).length)
    }

    @Test
    fun `a voice note waits to be heard, then is embedded by what was said`() {
        assertNull(EmbedText.want(Memory(type = MemoryType.AUDIO), hasPicture = false))
        val heard = EmbedText.want(Memory(type = MemoryType.AUDIO, extractedText = "the ramen place is closed on mondays"), hasPicture = false)
        assertTrue(heard is EmbedText.AsText && "ramen" in heard.prompt)
        // Heard with no words and no note: nothing to embed.
        assertNull(EmbedText.want(Memory(type = MemoryType.AUDIO, extractedText = ""), hasPicture = false))
        assertTrue(EmbedText.want(Memory(type = MemoryType.AUDIO, extractedText = "", note = "from priya"), hasPicture = false) is EmbedText.AsText)
    }
}
