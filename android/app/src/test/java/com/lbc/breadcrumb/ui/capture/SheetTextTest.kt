package com.lbc.breadcrumb.ui.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SheetTextTest {

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
}
