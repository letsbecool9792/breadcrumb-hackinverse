package com.lbc.breadcrumb.ui.capture

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SheetTextTest {

    private fun memory(type: MemoryType, from: String? = null) = Memory(type = type, sourceAppLabel = from)

    @Test
    fun `one item says its kind, and where it came from`() {
        assertEquals("image · from whatsapp", SheetText.kindLine(listOf(memory(MemoryType.IMAGE, "WhatsApp"))))
        assertEquals("link", SheetText.kindLine(listOf(memory(MemoryType.LINK))))
    }

    @Test
    fun `several of one kind are counted`() {
        val three = List(3) { memory(MemoryType.IMAGE, "Photos") }
        assertEquals("3 images · from photos", SheetText.kindLine(three))
    }

    @Test
    fun `the source is said only when every item agrees`() {
        val mixed = listOf(memory(MemoryType.IMAGE, "WhatsApp"), memory(MemoryType.IMAGE, "Telegram"))
        assertEquals("2 images", SheetText.kindLine(mixed))
        val someUnknown = listOf(memory(MemoryType.IMAGE, "WhatsApp"), memory(MemoryType.IMAGE, null))
        assertEquals("2 images", SheetText.kindLine(someUnknown))
    }

    @Test
    fun `mixed kinds are items`() {
        assertEquals("2 items · from files", SheetText.kindLine(listOf(memory(MemoryType.IMAGE, "Files"), memory(MemoryType.PDF, "Files"))))
    }

    @Test
    fun `a link card takes the first URL anywhere in the text`() {
        val card = SheetText.linkCard("Priya says apply early: https://www.linkedin.com/jobs/view/4120938817, thanks", null)

        assertEquals("linkedin.com", card?.host)
        assertEquals("https://www.linkedin.com/jobs/view/4120938817", card?.url)
        assertNull(card?.headline)
    }

    @Test
    fun `the page title is the headline when there is one`() {
        val card = SheetText.linkCard("https://youtu.be/abc?t=42", "  System design talk ")

        assertEquals("youtu.be", card?.host)
        assertEquals("System design talk", card?.headline)
        assertNull(SheetText.linkCard("https://youtu.be/abc", "   ")?.headline)
    }

    @Test
    fun `a bare www address has a host too`() {
        val card = SheetText.linkCard("see www.example.org/a.", null)

        assertEquals("example.org", card?.host)
        assertEquals("www.example.org/a", card?.url)
    }

    @Test
    fun `text without a URL has no link card`() {
        assertNull(SheetText.linkCard("Try the brewery burger in Indiranagar", "A title"))
        assertNull(SheetText.linkCard(null, null))
    }

    @Test
    fun `a share with some kept already says how many`() {
        val two = List(2) { memory(MemoryType.IMAGE, "Photos") }
        assertEquals("2 images · from photos · 1 kept already", SheetText.kindLine(two, keptAlready = 1))
    }

    @Test
    fun `kept already says what it is and when it was kept`() {
        val day = 86_400_000L
        val now = 10 * day
        assertEquals("link · kept 3d ago", SheetText.keptLine(listOf(Memory(type = MemoryType.LINK, capturedAt = now - 3 * day)), now))
        assertEquals("image · kept just now", SheetText.keptLine(listOf(Memory(type = MemoryType.IMAGE, capturedAt = now)), now))
        assertEquals("2 images · kept before", SheetText.keptLine(List(2) { memory(MemoryType.IMAGE) }, now))
    }

    @Test
    fun `a voice note is called one`() {
        assertEquals("voice note · from whatsapp", SheetText.kindLine(listOf(memory(MemoryType.AUDIO, "WhatsApp"))))
        assertEquals("2 voice notes", SheetText.kindLine(listOf(memory(MemoryType.AUDIO), memory(MemoryType.AUDIO))))
    }
}
