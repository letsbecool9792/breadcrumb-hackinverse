package com.lbc.breadcrumb.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftCodecTest {

    @Test
    fun `a round trip preserves text and attachments`() {
        val original = Draft(
            text = "Meeting summary and action items",
            attachments = listOf(
                DraftFile(
                    uri = "content://media/external/images/media/101",
                    name = "whiteboard.jpg",
                    isPdf = false,
                ),
                DraftFile(
                    uri = "content://com.android.providers.downloads.documents/document/42",
                    name = "roadmap.pdf",
                    isPdf = true,
                ),
            ),
        )

        val encoded = DraftCodec.encode(original)
        val decoded = DraftCodec.decode(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `a round trip with empty draft`() {
        val empty = Draft()
        val encoded = DraftCodec.encode(empty)
        val decoded = DraftCodec.decode(encoded)

        assertEquals(empty, decoded)
    }

    @Test
    fun `a round trip with text only`() {
        val textOnly = Draft(text = "Quick thought")
        val encoded = DraftCodec.encode(textOnly)
        val decoded = DraftCodec.decode(encoded)

        assertEquals(textOnly, decoded)
    }

    @Test
    fun `junk decodes to an empty draft`() {
        val junkInputs = listOf(
            null,
            "",
            "   ",
            "not json at all",
            "{",
            "{broken json",
            "[1, 2, 3]",
            "12345",
            "true",
            "\"just a string\"",
            "{\"attachments\": \"not a list\"}",
        )

        for (junk in junkInputs) {
            val decoded = DraftCodec.decode(junk)
            assertTrue("Expected empty draft for junk: $junk", decoded.isEmpty())
            assertEquals("", decoded.text)
            assertTrue(decoded.attachments.isEmpty())
        }
    }
}
