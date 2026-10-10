package com.lbc.breadcrumb.capture

import com.lbc.breadcrumb.data.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaRulesTest {

    @Test
    fun `the provider's type wins over the declared one, which fills in when it has none`() {
        assertEquals("image/png", MediaRules.mimeOf("image/png", "image/*"))
        assertEquals("application/pdf", MediaRules.mimeOf(null, "application/pdf"))
        assertEquals("image/*", MediaRules.mimeOf("", "image/*"))
        assertNull(MediaRules.mimeOf(null, null))
    }

    @Test
    fun `pictures and PDFs are kept, anything else is skipped`() {
        assertEquals(MemoryType.IMAGE, MediaRules.kindOf("image/jpeg"))
        assertEquals(MemoryType.IMAGE, MediaRules.kindOf("IMAGE/HEIC"))
        assertEquals(MemoryType.IMAGE, MediaRules.kindOf("image/*"))
        assertEquals(MemoryType.PDF, MediaRules.kindOf("application/pdf"))
        assertNull(MediaRules.kindOf("video/mp4"))
        assertNull(MediaRules.kindOf("text/plain"))
        assertNull(MediaRules.kindOf("application/zip"))
        assertNull(MediaRules.kindOf(null))
    }

    @Test
    fun `the extension comes from the fixed map, never the sender`() {
        assertEquals("jpg", MediaRules.extensionFor("image/jpeg"))
        assertEquals("png", MediaRules.extensionFor("image/png"))
        assertEquals("webp", MediaRules.extensionFor("image/webp"))
        assertEquals("gif", MediaRules.extensionFor("image/gif"))
        assertEquals("heic", MediaRules.extensionFor("image/heic"))
        assertEquals("heif", MediaRules.extensionFor("image/heif"))
        assertEquals("avif", MediaRules.extensionFor("image/avif"))
        assertEquals("bmp", MediaRules.extensionFor("image/bmp"))
        assertEquals("pdf", MediaRules.extensionFor("application/pdf"))
        assertEquals("jpg", MediaRules.extensionFor("image/*"))
        assertEquals("jpg", MediaRules.extensionFor("image/svg+xml"))
    }

    @Test
    fun `shared text goes on the file only when exactly one came`() {
        assertEquals("look at this https://x.com/a", MediaRules.captionFor("look at this https://x.com/a", 1))
        assertNull(MediaRules.captionFor("look at these", 3))
        assertNull(MediaRules.captionFor("   ", 1))
        assertNull(MediaRules.captionFor(null, 1))
    }

    @Test
    fun `a real subject is the file's title`() {
        assertEquals("Hackathon schedule", MediaRules.titleFor("Hackathon schedule", "IMG_0042.jpg", MemoryType.IMAGE))
        assertEquals("Lecture 4 notes", MediaRules.titleFor("Lecture 4 notes", "notes.pdf", MemoryType.PDF))
    }

    @Test
    fun `a generic subject, or one that is just the file name, is not`() {
        assertNull(MediaRules.titleFor("Photo from Priya", "IMG_0042.jpg", MemoryType.IMAGE))
        assertNull(MediaRules.titleFor("Image from Arjun", "IMG_0042.jpg", MemoryType.IMAGE))
        assertNull(MediaRules.titleFor("IMG_0042.jpg", "IMG_0042.jpg", MemoryType.IMAGE))
        assertNull(MediaRules.titleFor("img_0042", "IMG_0042.jpg", MemoryType.IMAGE))
        assertNull(MediaRules.titleFor(null, "IMG_0042.jpg", MemoryType.IMAGE))
    }

    @Test
    fun `a PDF without a real subject is titled by its file name, without the extension`() {
        assertEquals("Offer letter", MediaRules.titleFor(null, "Offer letter.pdf", MemoryType.PDF))
        assertEquals("Offer letter", MediaRules.titleFor("Document from HR", "Offer letter.PDF", MemoryType.PDF))
        assertEquals("Offer letter", MediaRules.titleFor("Offer letter.pdf", "Offer letter.pdf", MemoryType.PDF))
        assertNull(MediaRules.titleFor(null, null, MemoryType.PDF))
    }

    @Test
    fun `the content date is taken, else modified in seconds, else last modified`() {
        assertEquals(1_000L, MediaRules.contentDate(1_000L, 2L, 3_000L))
        assertEquals(2_000L, MediaRules.contentDate(null, 2L, 3_000L))
        assertEquals(2_000L, MediaRules.contentDate(0L, 2L, 3_000L))
        assertEquals(3_000L, MediaRules.contentDate(null, 0L, 3_000L))
        assertNull(MediaRules.contentDate(null, null, null))
        assertNull(MediaRules.contentDate(0L, -1L, 0L))
    }
}
