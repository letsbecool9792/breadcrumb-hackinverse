package com.lbc.breadcrumb.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryTest {

    @Test
    fun `a captioned photo with a link wears both chips, image first`() {
        val memory = Memory(type = MemoryType.IMAGE, hasLink = true)
        assertEquals(listOf(MemoryType.IMAGE, MemoryType.LINK), memory.chips)
    }

    @Test
    fun `a link is one chip, not two`() {
        val memory = Memory(type = MemoryType.LINK, hasLink = true)
        assertEquals(listOf(MemoryType.LINK), memory.chips)
    }

    @Test
    fun `text without a link is just text`() {
        assertEquals(listOf(MemoryType.TEXT), Memory(type = MemoryType.TEXT).chips)
    }

    @Test
    fun `searchable text joins every text field in a fixed order and skips blanks`() {
        val memory = Memory(
            type = MemoryType.IMAGE,
            title = "Internship post",
            note = "  ",
            summary = "LinkedIn post about a Qualcomm internship",
            rawText = null,
            extractedText = "Apply by April 30",
        )
        assertEquals(
            "Internship post\nLinkedIn post about a Qualcomm internship\nApply by April 30",
            memory.searchableText,
        )
    }
}
