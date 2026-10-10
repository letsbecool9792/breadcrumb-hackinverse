package com.lbc.breadcrumb.capture

import com.lbc.breadcrumb.data.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Test

class TextRulesTest {
    @Test
    fun parseText_withHttpLink_returnsLinkAndTrue() {
        val (type, hasLink) = TextRules.parseText("Check this out: http://example.com")
        assertEquals(MemoryType.LINK, type)
        assertEquals(true, hasLink)
    }

    @Test
    fun parseText_withHttpsLink_returnsLinkAndTrue() {
        val (type, hasLink) = TextRules.parseText("https://example.com is a great site")
        assertEquals(MemoryType.LINK, type)
        assertEquals(true, hasLink)
    }

    @Test
    fun parseText_withoutLink_returnsTextAndFalse() {
        val (type, hasLink) = TextRules.parseText("Just some plain text")
        assertEquals(MemoryType.TEXT, type)
        assertEquals(false, hasLink)
    }

    @Test
    fun `no subject is no title`() {
        assertEquals(null, TextRules.titleFor(null, "some text"))
        assertEquals(null, TextRules.titleFor("   ", "some text"))
    }

    @Test
    fun `a chat app's generic subject is no title`() {
        assertEquals(null, TextRules.titleFor("Photo from Aritra", "some text"))
        assertEquals(null, TextRules.titleFor("Video from John Doe", "some text"))
        assertEquals(null, TextRules.titleFor("Document from Priya", "some text"))
        assertEquals(null, TextRules.titleFor("file from priya", "some text"))
    }

    @Test
    fun `a subject that repeats the text is no title`() {
        assertEquals(null, TextRules.titleFor("https://example.com/a", " https://example.com/a "))
    }

    @Test
    fun `a real subject is the title, trimmed`() {
        assertEquals("Software Engineering Intern, Qualcomm", TextRules.titleFor("  Software Engineering Intern, Qualcomm ", "https://www.linkedin.com/jobs/view/1"))
    }
}
