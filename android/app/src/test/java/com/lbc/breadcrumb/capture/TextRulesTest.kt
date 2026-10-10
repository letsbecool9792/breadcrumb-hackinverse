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
    fun cleanSubject_withNull_returnsNull() {
        assertEquals(null, TextRules.cleanSubject(null))
    }

    @Test
    fun cleanSubject_withGenericWhatsAppSubject_returnsNull() {
        assertEquals(null, TextRules.cleanSubject("Photo from Aritra"))
        assertEquals(null, TextRules.cleanSubject("Video from John Doe"))
    }

    @Test
    fun cleanSubject_withValidSubject_returnsTrimmedSubject() {
        assertEquals("Valid Title", TextRules.cleanSubject("  Valid Title  "))
    }
}
