package com.lbc.breadcrumb.ui.home

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.SyncState
import com.lbc.breadcrumb.ui.home.DetailText.Found
import com.lbc.breadcrumb.ui.home.DetailText.NoteEdit
import com.lbc.breadcrumb.ui.home.DetailText.Provenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class DetailTextTest {

    private val utc = ZoneId.of("UTC")
    private val day = 86_400_000L
    private val sept12 = 1_757_635_200_000L // 2025-09-12 00:00 UTC

    @Test
    fun `a picture has a title, its caption, and what the model made of it`() {
        val picture = Memory(
            type = MemoryType.IMAGE,
            title = "Offer letter",
            rawText = "Priya sent this on Friday",
            summary = "A job offer from Qualcomm",
        )

        val heading = DetailText.heading(picture)

        assertEquals("Offer letter", heading.title)
        assertEquals("Priya sent this on Friday", heading.caption)
        assertEquals("A job offer from Qualcomm", heading.summary)
    }

    @Test
    fun `a caption or summary that repeats the title is left out`() {
        val picture = Memory(type = MemoryType.IMAGE, rawText = "Same words", summary = "Same words")

        val heading = DetailText.heading(picture)

        assertEquals("Same words", heading.title)
        assertNull(heading.caption)
        assertNull(heading.summary)
    }

    @Test
    fun `a pdf is titled Document when it has no title`() {
        assertEquals("Document", DetailText.heading(Memory(type = MemoryType.PDF)).title)
    }

    @Test
    fun `a link or a note has only its summary under it`() {
        val link = Memory(type = MemoryType.LINK, rawText = "https://example.com", title = "A page", summary = "About a page")
        val note = Memory(type = MemoryType.TEXT, rawText = "Try the burger", summary = "A restaurant tip")

        assertEquals(DetailText.Heading(null, null, "About a page"), DetailText.heading(link))
        assertEquals(DetailText.Heading(null, null, "A restaurant tip"), DetailText.heading(note))
    }

    @Test
    fun `a link or note with nothing to say has no heading`() {
        assertTrue(DetailText.heading(Memory(type = MemoryType.TEXT, rawText = "Try the burger")).isEmpty)
    }

    @Test
    fun `provenance gives saved, the source in lowercase, the day taken and the status`() {
        val memory = Memory(
            type = MemoryType.IMAGE,
            capturedAt = sept12,
            sourceAppLabel = "WhatsApp",
            contentCreatedAt = sept12 - 150 * day,
            syncState = SyncState.PENDING,
            localUri = "file:///x.png",
            extractedText = "words",
        )

        val rows = DetailText.provenance(memory, now = sept12 + 2 * day, zone = utc)

        assertEquals(
            listOf(
                Provenance.SAVED to "12 sep · 2 days ago",
                Provenance.FROM to "whatsapp",
                Provenance.TAKEN to "15 apr",
                Provenance.STATUS to "not sent yet · words only",
            ),
            rows,
        )
    }

    @Test
    fun `provenance leaves out what is not known`() {
        val memory = Memory(type = MemoryType.TEXT, capturedAt = sept12, syncState = SyncState.SYNCED)

        val rows = DetailText.provenance(memory, now = sept12, zone = utc)

        assertEquals(listOf(Provenance.SAVED to "12 sep · today"), rows)
    }

    @Test
    fun `found text is in two sections, and a link's is from the page`() {
        val picture = Memory(type = MemoryType.IMAGE, extractedText = "Qualcomm is hiring", readText = "A whiteboard")
        val link = Memory(type = MemoryType.LINK, extractedText = "Apply by April 30")

        assertEquals(
            listOf(Found.TEXT_FOUND to "Qualcomm is hiring", Found.WHAT_IT_SHOWS to "A whiteboard"),
            DetailText.foundText(picture),
        )
        assertEquals(listOf(Found.FROM_THE_PAGE to "Apply by April 30"), DetailText.foundText(link))
    }

    @Test
    fun `nothing read means no found text`() {
        assertTrue(DetailText.foundText(Memory(type = MemoryType.IMAGE, extractedText = "", readText = "  ")).isEmpty())
    }

    @Test
    fun `long found text shows its first 1,600 characters, cut at a word`() {
        val long = "word ".repeat(1_000)

        val shown = DetailText.foundText(Memory(type = MemoryType.PDF, extractedText = long)).single().second

        assertTrue(shown.length <= DetailText.FOUND_TEXT_LIMIT + 2)
        assertTrue(shown.endsWith(" …"))
    }

    @Test
    fun `a note typed is written, blank clears it, and the same words change nothing`() {
        assertEquals(NoteEdit.Write("from Priya"), DetailText.noteEdit("  from Priya ", null))
        assertEquals(NoteEdit.Write(null), DetailText.noteEdit("   ", "from Priya"))
        assertEquals(NoteEdit.Unchanged, DetailText.noteEdit("from Priya", "from Priya"))
        assertEquals(NoteEdit.Unchanged, DetailText.noteEdit("", null))
        assertEquals(NoteEdit.Write("new"), DetailText.noteEdit("new", "old"))
    }
}
