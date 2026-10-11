package com.lbc.breadcrumb.ui.home

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadlineTest {

    @Test
    fun `screenshot skips clock time and lone inbox word to pick first real line`() {
        val text = """
            12:20
            Congrats, you've been accepted
            Inbox
            to ETHGlobal Mumbai Hackathon!
        """.trimIndent()
        val memory = Memory(type = MemoryType.IMAGE, extractedText = text)
        assertEquals("Congrats, you've been accepted", Headline.of(memory))
    }

    @Test
    fun `screenshot skips network lines, battery percentages and symbols`() {
        val text = """
            12:20 PM
            KB/S LTE 4G
            85%
            ---
            •••••
            to me
            Quarterly Financial Report Q3
            Revenue grew 25% year over year
        """.trimIndent()
        val memory = Memory(type = MemoryType.IMAGE, extractedText = text)
        assertEquals("Quarterly Financial Report Q3", Headline.of(memory))
    }

    @Test
    fun `screenshot headline is capped past 90 characters at word boundary`() {
        val longLine = "Alpha Beta Gamma Delta Epsilon Zeta Eta Theta Iota Kappa Lambda Mu Nu Xi Omicron Pi Rho Sigma Tau Upsilon Phi Chi Psi Omega"
        val memory = Memory(type = MemoryType.IMAGE, extractedText = "12:20\n$longLine\nFooter")
        val headline = Headline.of(memory)
        assertNotNull(headline)
        assertEquals(ResultText.firstLine(longLine), headline)
        assertTrue(headline!!.endsWith("…"))
        assertTrue(headline.length <= 90)
    }

    @Test
    fun `link uses page title`() {
        val link = Memory(type = MemoryType.LINK, title = "Kotlin 2.0 Released", rawText = "https://kotlinlang.org")
        assertEquals("Kotlin 2.0 Released", Headline.of(link))
    }

    @Test
    fun `link without title returns null`() {
        val link = Memory(type = MemoryType.LINK, rawText = "https://kotlinlang.org")
        assertNull(Headline.of(link))
    }

    @Test
    fun `pdf uses pdf title when present`() {
        val pdf = Memory(type = MemoryType.PDF, title = "Resume - Jane Doe", extractedText = "Jane Doe\nSoftware Engineer")
        assertEquals("Resume - Jane Doe", Headline.of(pdf))
    }

    @Test
    fun `pdf uses first real line when title is absent`() {
        val pdf = Memory(type = MemoryType.PDF, title = null, extractedText = "\n---\nResearch Paper on Distributed Systems\nAbstract...")
        assertEquals("Research Paper on Distributed Systems", Headline.of(pdf))
    }

    @Test
    fun `note uses first non-blank line`() {
        val note = Memory(type = MemoryType.TEXT, rawText = "\n\nGrocery shopping list\nMilk\nEggs")
        assertEquals("Grocery shopping list", Headline.of(note))
    }

    @Test
    fun `screenshot with only chrome returns null`() {
        val chromeOnly = Memory(type = MemoryType.IMAGE, extractedText = "12:20\n4G LTE\n100%\n---")
        assertNull(Headline.of(chromeOnly))
    }

    @Test
    fun `ResultText title uses Headline for screenshot before falling back to Image`() {
        val text = """
            12:20
            Congrats, you've been accepted
            Inbox
            to ETHGlobal Mumbai Hackathon!
        """.trimIndent()
        val memory = Memory(type = MemoryType.IMAGE, extractedText = text)
        assertEquals("Congrats, you've been accepted", ResultText.title(memory))
    }

    @Test
    fun `ResultText title prefers existing summary over Headline`() {
        val text = """
            12:20
            Congrats, you've been accepted
            Inbox
            to ETHGlobal Mumbai Hackathon!
        """.trimIndent()
        val memory = Memory(type = MemoryType.IMAGE, extractedText = text, summary = "ETHGlobal Mumbai Acceptance")
        assertEquals("ETHGlobal Mumbai Acceptance", ResultText.title(memory))
    }
}
