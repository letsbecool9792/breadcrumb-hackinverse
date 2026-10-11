package com.lbc.breadcrumb.ui.home

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetTest {

    @Test
    fun `snippet returns text when under 90 characters`() {
        val memory = Memory(type = MemoryType.TEXT, rawText = "Qualcomm is hiring SWE interns for summer.")
        val snippet = Snippet.of(memory, listOf("intern"))
        assertEquals("Qualcomm is hiring SWE interns for summer.", snippet)
    }

    @Test
    fun `snippet matching is case-insensitive`() {
        val memory = Memory(type = MemoryType.TEXT, rawText = "ETHGlobal Mumbai Hackathon acceptance letter")
        val snippet = Snippet.of(memory, listOf("HACKATHON"))
        assertEquals("ETHGlobal Mumbai Hackathon acceptance letter", snippet)
    }

    @Test
    fun `snippet cuts window around best match with ellipsis`() {
        val text = "Lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua hackathon enim ad minim veniam quis nostrud exercitation ullamco"
        val memory = Memory(type = MemoryType.IMAGE, extractedText = text)
        val snippet = Snippet.of(memory, listOf("hackathon"))
        assertNotNull(snippet)
        assertTrue(snippet!!.contains("hackathon", ignoreCase = true))
        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.endsWith("…"))
        assertTrue(snippet.length <= 95)
    }

    @Test
    fun `snippet searches across note`() {
        val memory = Memory(type = MemoryType.IMAGE, extractedText = "image text", note = "Important receipt from Apple Store")
        val snippet = Snippet.of(memory, listOf("apple"))
        assertEquals("Important receipt from Apple Store", snippet)
    }

    @Test
    fun `snippet searches across title`() {
        val memory = Memory(type = MemoryType.LINK, title = "Design guidelines for Android", rawText = "https://example.com")
        val snippet = Snippet.of(memory, listOf("guidelines"))
        assertEquals("Design guidelines for Android", snippet)
    }

    @Test
    fun `snippet searches across extractedText`() {
        val memory = Memory(type = MemoryType.IMAGE, extractedText = "Confirmation code: 849204 for verification")
        val snippet = Snippet.of(memory, listOf("verification"))
        assertEquals("Confirmation code: 849204 for verification", snippet)
    }

    @Test
    fun `snippet returns null when no query word matches`() {
        val memory = Memory(type = MemoryType.TEXT, rawText = "Grocery shopping list")
        val snippet = Snippet.of(memory, listOf("hackathon"))
        assertNull(snippet)
    }

    @Test
    fun `snippet returns null when words list is empty`() {
        val memory = Memory(type = MemoryType.TEXT, rawText = "Grocery shopping list")
        val snippet = Snippet.of(memory, emptyList())
        assertNull(snippet)
    }

    @Test
    fun `snippet collapses newlines into single line window`() {
        val text = """
            12:20
            Congrats, you've been accepted
            Inbox
            to ETHGlobal Mumbai Hackathon!
        """.trimIndent()
        val memory = Memory(type = MemoryType.IMAGE, extractedText = text)
        val snippet = Snippet.of(memory, listOf("mumbai"))
        assertNotNull(snippet)
        assertFalse(snippet!!.contains("\n"))
        assertTrue(snippet.contains("Mumbai", ignoreCase = true))
    }

    @Test
    fun `RowText uses Snippet when there is no summary`() {
        val memory = Memory(
            id = "m1",
            type = MemoryType.IMAGE,
            extractedText = "Prefix words something about hackathon and other details",
            summary = null,
        )
        val row = rowText(memory, hit = null, words = listOf("hackathon"))
        assertNotNull(row.why)
    }

    @Test
    fun `RowText summary always wins when present and different from title`() {
        val memory = Memory(
            id = "m1",
            type = MemoryType.IMAGE,
            title = "Screenshot from Dex",
            extractedText = "Some text with no query words",
            summary = "Summary about Ethereum",
        )
        val row = rowText(memory, hit = null, words = listOf("unmatchedquery"))
        assertEquals(Why.Summary("Summary about Ethereum"), row.why)
    }
}
