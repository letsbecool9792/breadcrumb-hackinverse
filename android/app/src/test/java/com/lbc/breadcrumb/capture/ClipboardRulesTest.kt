package com.lbc.breadcrumb.capture

import com.lbc.breadcrumb.capture.ClipboardRules.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardRulesTest {

    @Test
    fun `a sensitive clip is never saved, whatever it holds`() {
        assertEquals(Decision.Sensitive, ClipboardRules.decide(true, listOf("hunter2"), 0))
        assertEquals(Decision.Sensitive, ClipboardRules.decide(true, emptyList(), 2))
        assertEquals(Decision.Sensitive, ClipboardRules.decide(true, emptyList(), 0))
    }

    @Test
    fun `text wins over files`() {
        assertEquals(Decision.Text("https://example.com/a"), ClipboardRules.decide(false, listOf("https://example.com/a"), 1))
    }

    @Test
    fun `several texts join, one per line, trimmed, blanks dropped`() {
        assertEquals(Decision.Text("first\nsecond"), ClipboardRules.decide(false, listOf(" first ", "   ", "second"), 0))
    }

    @Test
    fun `files when there is no text`() {
        assertEquals(Decision.Files, ClipboardRules.decide(false, listOf("  "), 1))
    }

    @Test
    fun `nothing at all is empty`() {
        assertEquals(Decision.Empty, ClipboardRules.decide(false, emptyList(), 0))
        assertEquals(Decision.Empty, ClipboardRules.decide(false, listOf(""), 0))
    }
}
