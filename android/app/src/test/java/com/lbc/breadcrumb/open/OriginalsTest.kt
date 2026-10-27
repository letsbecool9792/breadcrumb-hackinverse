package com.lbc.breadcrumb.open

import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * JVM unit tests for pure decision logic of [OriginalAction] and [ShareOut]
 * (docs/design.md §12, docs/workstreams/reading.md R7).
 */
class OriginalsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun fileActionAndShareForExistingImageWithNote() {
        val file = tempFolder.newFile("sample.jpg")
        val memory = Memory(
            type = MemoryType.IMAGE,
            note = "Internship banner",
        )

        val action = OriginalAction.decide(memory, file)
        assertTrue(action is OriginalAction.ViewFile)
        val viewFile = action as OriginalAction.ViewFile
        assertEquals(file, viewFile.file)
        assertEquals("image/jpeg", viewFile.mime)
        assertEquals(R.string.detail_action_open_original, viewFile.labelRes)
        assertTrue(viewFile.leavesApp)
        assertTrue(viewFile.isEnabled)

        val share = ShareOut.decide(memory, file)
        assertNotNull(share)
        assertTrue(share is ShareOut.File)
        val fileShare = share as ShareOut.File
        assertEquals(file, fileShare.file)
        assertEquals("image/jpeg", fileShare.mime)
        assertEquals("Internship banner", fileShare.note)
    }

    @Test
    fun fileActionAndShareForExistingPdf() {
        val file = tempFolder.newFile("report.pdf")
        val memory = Memory(
            type = MemoryType.PDF,
            note = "Quarterly results",
        )

        val action = OriginalAction.decide(memory, file)
        assertTrue(action is OriginalAction.ViewFile)
        val viewFile = action as OriginalAction.ViewFile
        assertEquals(file, viewFile.file)
        assertEquals("application/pdf", viewFile.mime)

        val share = ShareOut.decide(memory, file)
        assertNotNull(share)
        assertTrue(share is ShareOut.File)
        val fileShare = share as ShareOut.File
        assertEquals(file, fileShare.file)
        assertEquals("application/pdf", fileShare.mime)
        assertEquals("Quarterly results", fileShare.note)
    }

    @Test
    fun linkWithUrlActionAndShare() {
        val memory = Memory(
            type = MemoryType.LINK,
            rawText = "Check this page: https://example.com/internship",
            title = "Qualcomm SWE Internship",
            note = "Apply by Friday",
        )

        val action = OriginalAction.decide(memory, null)
        assertTrue(action is OriginalAction.ViewUrl)
        val viewUrl = action as OriginalAction.ViewUrl
        assertEquals("https://example.com/internship", viewUrl.url)
        assertEquals(R.string.detail_action_open_link, viewUrl.labelRes)
        assertTrue(viewUrl.leavesApp)
        assertTrue(viewUrl.isEnabled)

        val share = ShareOut.decide(memory, null)
        assertNotNull(share)
        assertTrue(share is ShareOut.Text)
        val textShare = share as ShareOut.Text
        assertEquals("Apply by Friday\nhttps://example.com/internship", textShare.text)
        assertEquals("Qualcomm SWE Internship", textShare.subject)
    }

    @Test
    fun linkWithBareWwwGetsHttpsPrefix() {
        val memory = Memory(
            type = MemoryType.LINK,
            rawText = "www.hackinverse.org",
            title = "Hackinverse Home",
        )

        val action = OriginalAction.decide(memory, null)
        assertTrue(action is OriginalAction.ViewUrl)
        assertEquals("https://www.hackinverse.org", (action as OriginalAction.ViewUrl).url)

        val share = ShareOut.decide(memory, null)
        assertNotNull(share)
        assertTrue(share is ShareOut.Text)
        val textShare = share as ShareOut.Text
        assertEquals("https://www.hackinverse.org", textShare.text)
        assertEquals("Hackinverse Home", textShare.subject)
    }

    @Test
    fun linkWithoutUrlFallsBackToWords() {
        val memory = Memory(
            type = MemoryType.LINK,
            rawText = "Not an actual link URL here",
            note = "Chat snippet",
        )

        val action = OriginalAction.decide(memory, null)
        assertTrue(action is OriginalAction.CopyText)
        val copyText = action as OriginalAction.CopyText
        assertEquals("Not an actual link URL here", copyText.text)
        assertEquals(R.string.detail_action_copy_text, copyText.labelRes)
        assertFalse(copyText.leavesApp)
        assertTrue(copyText.isEnabled)

        val share = ShareOut.decide(memory, null)
        assertNotNull(share)
        assertTrue(share is ShareOut.Text)
        val textShare = share as ShareOut.Text
        assertEquals("Not an actual link URL here\n\nChat snippet", textShare.text)
        assertNull(textShare.subject)
    }

    @Test
    fun noteActionAndShareWithAndWithoutNote() {
        val memoryWithNote = Memory(
            type = MemoryType.TEXT,
            rawText = "Great cafe near the station",
            note = "Try the cold brew",
        )

        val action1 = OriginalAction.decide(memoryWithNote, null)
        assertTrue(action1 is OriginalAction.CopyText)
        assertEquals("Great cafe near the station", (action1 as OriginalAction.CopyText).text)

        val share1 = ShareOut.decide(memoryWithNote, null)
        assertNotNull(share1)
        assertTrue(share1 is ShareOut.Text)
        assertEquals("Great cafe near the station\n\nTry the cold brew", (share1 as ShareOut.Text).text)

        val memoryWordsOnly = Memory(
            type = MemoryType.TEXT,
            rawText = "Short reminder text",
            note = null,
        )
        val share2 = ShareOut.decide(memoryWordsOnly, null)
        assertNotNull(share2)
        assertEquals("Short reminder text", (share2 as ShareOut.Text).text)
    }

    @Test
    fun missingFileActionAndShare() {
        val nonExistentFile = File(tempFolder.root, "deleted.png")
        val memory = Memory(
            type = MemoryType.IMAGE,
            note = "Some note",
        )

        val actionWithMissingFile = OriginalAction.decide(memory, nonExistentFile)
        assertEquals(OriginalAction.Missing, actionWithMissingFile)
        assertEquals(R.string.detail_action_missing, actionWithMissingFile.labelRes)
        assertFalse(actionWithMissingFile.leavesApp)
        assertFalse(actionWithMissingFile.isEnabled)

        val shareWithMissingFile = ShareOut.decide(memory, nonExistentFile)
        assertNull("Missing file should result in no share button", shareWithMissingFile)

        val actionWithNullFile = OriginalAction.decide(memory, null)
        assertEquals(OriginalAction.Missing, actionWithNullFile)
        assertNull(ShareOut.decide(memory, null))
    }

    @Test
    fun emptyTextActionAndShare() {
        val memory = Memory(
            type = MemoryType.TEXT,
            rawText = null,
            note = null,
        )

        assertEquals(OriginalAction.Missing, OriginalAction.decide(memory, null))
        assertNull(ShareOut.decide(memory, null))
    }

    @Test
    fun mimeTypeDetection() {
        assertEquals("application/pdf", OriginalAction.mimeFor(MemoryType.PDF, File("doc.pdf")))
        assertEquals("image/jpeg", OriginalAction.mimeFor(MemoryType.IMAGE, File("photo.jpg")))
        assertEquals("image/jpeg", OriginalAction.mimeFor(MemoryType.IMAGE, File("photo.jpeg")))
        assertEquals("image/png", OriginalAction.mimeFor(MemoryType.IMAGE, File("screenshot.png")))
        assertEquals("image/webp", OriginalAction.mimeFor(MemoryType.IMAGE, File("image.webp")))
        assertEquals("image/gif", OriginalAction.mimeFor(MemoryType.IMAGE, File("anim.gif")))
        assertEquals("image/heic", OriginalAction.mimeFor(MemoryType.IMAGE, File("photo.heic")))
        assertEquals("image/heif", OriginalAction.mimeFor(MemoryType.IMAGE, File("photo.heif")))
        assertEquals("image/*", OriginalAction.mimeFor(MemoryType.IMAGE, File("unknown.xyz")))
    }

    @Test
    fun aVoiceNoteOpensAndGoesAsItsFile() {
        val file = tempFolder.newFile("note.opus")
        val memory = Memory(type = MemoryType.AUDIO, note = "from priya")

        val action = OriginalAction.decide(memory, file)
        assertEquals(OriginalAction.ViewFile(file, "audio/ogg"), action)
        assertEquals(ShareOut.File(file, "audio/ogg", "from priya"), ShareOut.decide(memory, file))
        assertEquals("audio/mp4", OriginalAction.mimeFor(MemoryType.AUDIO, File("a.m4a")))
        assertEquals(OriginalAction.Missing, OriginalAction.decide(memory, null))
        assertNull(ShareOut.decide(memory, null))
    }
}
