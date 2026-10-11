package com.lbc.breadcrumb.ui.home

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.net.SearchHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RowTextTest {

    private val image = Memory(
        id = "m1",
        type = MemoryType.IMAGE,
        extractedText = "Qualcomm is hiring interns, apply by April 30",
        summary = "LinkedIn post: Qualcomm internship",
    )

    @Test
    fun `a word found in the text is lit, with the words around it`() {
        val row = rowText(image, hit = null, words = listOf("intern"))

        val why = row.why as Why.Fragment
        assertEquals("interns", why.hit)
        assertEquals("Qualcomm is hiring ", why.lead)
        assertEquals(", apply by April 30", why.tail)
    }

    @Test
    fun `a match by meaning alone says what the model made of it`() {
        val link = Memory(
            id = "l",
            type = MemoryType.LINK,
            rawText = "https://example.com/jobs/4120",
            title = "Software Engineering Intern",
            summary = "A job posting at Qualcomm",
        )

        val row = rowText(link, hit = null, words = listOf("recruiting"))

        assertEquals("Software Engineering Intern", row.title)
        assertEquals(Why.Summary("A job posting at Qualcomm"), row.why)
    }

    @Test
    fun `nothing is said when the summary is the title already`() {
        val memory = image.copy(extractedText = null, title = null)

        val row = rowText(memory, hit = null, words = listOf("recruiting"))

        assertEquals("LinkedIn post: Qualcomm internship", row.title)
        assertNull(row.why)
    }

    @Test
    fun `the server's summary is preferred to the phone's copy`() {
        val hit = SearchHit(id = "m1", summary = "Hackathon acceptance email")

        val row = rowText(image, hit, words = listOf("recruiting"))

        // The hit's summary is the title here, so there is nothing more to say about it.
        assertEquals("Hackathon acceptance email", row.title)
        assertNull(row.why)
    }

    @Test
    fun `the text the model read is searched too`() {
        val photo = Memory(id = "p", type = MemoryType.IMAGE, extractedText = "")
        val hit = SearchHit(id = "p", readText = "a whiteboard with Qualcomm roadmap written on it")

        val why = rowText(photo, hit, words = listOf("roadmap")).why as Why.Fragment

        assertEquals("roadmap", why.hit)
    }

    @Test
    fun `no words to light and no summary leaves just the title`() {
        val note = Memory(id = "n", type = MemoryType.TEXT, rawText = "Try the brewery burger")

        val row = rowText(note, hit = null, words = emptyList())

        assertEquals("Try the brewery burger", row.title)
        assertNull(row.why)
    }

    @Test
    fun `the words to light are what the server took the phrase to mean`() {
        assertEquals(listOf("internship"), wordsToLight("that internship screenshot from april", "internship"))
    }

    @Test
    fun `without an interpretation they are the phrase's own words, filler dropped`() {
        assertEquals(listOf("guy", "qualcomm", "hackathon"), wordsToLight("that guy from the Qualcomm hackathon", null))
        assertEquals(listOf("qualcomm"), wordsToLight("qualcomm", "  "))
    }
}
