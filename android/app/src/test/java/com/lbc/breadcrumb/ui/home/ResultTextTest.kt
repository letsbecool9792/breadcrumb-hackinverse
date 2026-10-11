package com.lbc.breadcrumb.ui.home

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.SyncState
import com.lbc.breadcrumb.net.Interpretation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ResultTextTest {

    @Test
    fun `words extracts lowercase non-filler words of 2 or more characters`() {
        val phrase = "that internship screenshot from April, with Qualcomm!"
        val words = ResultText.words(phrase)
        assertEquals(listOf("internship", "screenshot", "april", "qualcomm"), words)
    }

    @Test
    fun `words drops filler words and single characters`() {
        val phrase = "i saved a thing and some stuff about it on the web"
        val words = ResultText.words(phrase)
        assertEquals(listOf("web"), words)
    }

    @Test
    fun `fragment finds hit and lights entire word when matching prefix`() {
        val texts = listOf("Qualcomm is hiring SWE interns for summer.")
        val words = listOf("intern")
        val frag = ResultText.fragment(texts, words)
        requireNotNull(frag)
        assertEquals("Qualcomm is hiring SWE ", frag.lead)
        assertEquals("interns", frag.hit)
        assertEquals(" for summer.", frag.tail)
    }

    @Test
    fun `fragment searches in prioritized order`() {
        val texts = listOf(
            null, // rawText
            "Note with qualcomm", // note
            "Extracted text with qualcomm", // extractedText
        )
        val words = listOf("qualcomm")
        val frag = ResultText.fragment(texts, words)
        requireNotNull(frag)
        assertEquals("Note with ", frag.lead)
        assertEquals("qualcomm", frag.hit)
    }

    @Test
    fun `fragment returns null when no word matches`() {
        val texts = listOf("A completely unrelated memory text")
        val words = listOf("qualcomm")
        assertNull(ResultText.fragment(texts, words))
    }

    @Test
    fun `fragment trims and cuts lead and tail at word boundaries with ellipsis`() {
        val leadIn = "word ".repeat(15) // ~75 chars
        val tailEnd = " end".repeat(30) // ~120 chars
        val text = "$leadIn intern $tailEnd"
        val frag = ResultText.fragment(listOf(text), listOf("intern"))
        requireNotNull(frag)
        assertEquals("intern", frag.hit)
        assert(frag.lead.startsWith("…")) { "Expected lead to start with '…', got ${frag.lead}" }
        assert(frag.tail.endsWith("…")) { "Expected tail to end with '…', got ${frag.tail}" }
    }

    @Test
    fun `title follows type precedence rules`() {
        val linkWithTitle = Memory(type = MemoryType.LINK, title = "Google", rawText = "https://google.com")
        assertEquals("Google", ResultText.title(linkWithTitle))

        val linkWithoutTitle = Memory(type = MemoryType.LINK, rawText = "https://example.com")
        assertEquals("https://example.com", ResultText.title(linkWithoutTitle))

        val bareLink = Memory(type = MemoryType.LINK)
        assertEquals("Link", ResultText.title(bareLink))

        val textMemory = Memory(type = MemoryType.TEXT, rawText = "First line of thought\nSecond line", title = "Some Title")
        assertEquals("First line of thought", ResultText.title(textMemory))

        val imageMemory = Memory(type = MemoryType.IMAGE, summary = "Image summary")
        assertEquals("Image summary", ResultText.title(imageMemory))

        val pdfMemory = Memory(type = MemoryType.PDF)
        assertEquals("Document", ResultText.title(pdfMemory))
    }

    @Test
    fun `firstLine picks first non-blank line and truncates at word past 90 chars`() {
        val text = "\n   \nFirst line of text is short\nSecond line"
        assertEquals("First line of text is short", ResultText.firstLine(text))

        val longLine = "Alpha Beta Gamma Delta Epsilon Zeta Eta Theta Iota Kappa Lambda Mu Nu Xi Omicron Pi Rho Sigma Tau Upsilon Phi Chi Psi Omega"
        val truncated = ResultText.firstLine(longLine, max = 30)
        assertEquals("Alpha Beta Gamma Delta…", truncated)
    }

    @Test
    fun `opening cuts at word with space ellipsis when exceeding max`() {
        val text = "Alpha Beta Gamma Delta Epsilon"
        val opened = ResultText.opening(text, max = 17)
        assertEquals("Alpha Beta Gamma …", opened)
    }

    @Test
    fun `host extracts clean domain without www and only when containing dot`() {
        assertEquals("instagram.com", ResultText.host("https://www.instagram.com/p/12345/"))
        assertEquals("youtu.be", ResultText.host("check this out: www.youtu.be/xyz!"))
        assertEquals("example.com", ResultText.host("http://example.com:8080/path"))
        assertNull(ResultText.host("localhost"))
        assertNull(ResultText.host("no url here"))
    }

    @Test
    fun `age formats durations according to design spec`() {
        val now = 1_000_000_000L
        assertEquals("now", ResultText.age(now - 30 * 1000L, now))
        assertEquals("15m", ResultText.age(now - 15 * 60 * 1000L, now))
        assertEquals("4h", ResultText.age(now - 4 * 3600 * 1000L, now))
        assertEquals("3d", ResultText.age(now - 3 * 86400 * 1000L, now))
        assertEquals("2w", ResultText.age(now - 14 * 86400 * 1000L, now))
        assertEquals("5mo", ResultText.age(now - 150 * 86400 * 1000L, now))
        assertEquals("2y", ResultText.age(now - 750 * 86400 * 1000L, now))
    }

    @Test
    fun `tileMeta and rowMeta format correctly`() {
        val now = 1_000_000_000L
        val memory = Memory(
            type = MemoryType.IMAGE,
            hasLink = true,
            sourceAppLabel = "WhatsApp",
            capturedAt = now - 2 * 86400 * 1000L,
        )
        assertEquals("2d · whatsapp", ResultText.tileMeta(memory, now))
        assertEquals("2d · whatsapp · image + link", ResultText.rowMeta(memory, now))
    }

    @Test
    fun `status formats reading and sending states`() {
        val readingLink = Memory(type = MemoryType.LINK, extractedText = null, syncState = SyncState.PENDING)
        assertEquals("page not read yet · not sent yet", ResultText.status(readingLink))
        // With cloud reading off, nothing is waiting to be sent.
        assertEquals("page not read yet", ResultText.status(readingLink, cloudReads = false))

        val readingImage = Memory(type = MemoryType.IMAGE, localUri = "file://img.jpg", extractedText = null, syncState = SyncState.UPLOADING)
        assertEquals("still reading it · not sent yet", ResultText.status(readingImage))

        val refused = Memory(type = MemoryType.TEXT, extractedText = "text", syncState = SyncState.FAILED)
        assertEquals("the server refused it", ResultText.status(refused))

        val synced = Memory(type = MemoryType.TEXT, extractedText = "text", syncState = SyncState.SYNCED)
        assertNull(ResultText.status(synced))
    }

    @Test
    fun `kept formats total and unsent counts`() {
        assertEquals("17 kept", ResultText.kept(17, 0))
        assertEquals("17 kept · 2 unsent", ResultText.kept(17, 2))
    }

    @Test
    fun `dates formats single days, whole months, and ranges`() {
        val today = LocalDate.of(2026, 10, 11)

        assertEquals("apr", ResultText.dates("2026-04-01", "2026-04-30", today))
        assertEquals("apr 2025", ResultText.dates("2025-04-01", "2025-04-30", today))
        assertEquals("18 sep", ResultText.dates("2026-09-18", "2026-09-18", today))
        assertEquals("1 apr – 15 may", ResultText.dates("2026-04-01", "2026-05-15", today))
        assertEquals("since 1 apr", ResultText.dates("2026-04-01", null, today))
        assertEquals("until 30 apr", ResultText.dates(null, "2026-04-30", today))
    }

    @Test
    fun `taken and monthOf format correctly`() {
        val zone = ZoneId.of("UTC")
        val epoch = LocalDate.of(2026, 4, 18).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals("18 apr", ResultText.taken(epoch, zone))
        assertEquals("apr", ResultText.monthOf(LocalDate.of(2026, 4, 18)))
    }

    @Test
    fun `saved formats date with relative age`() {
        val zone = ZoneId.of("UTC")
        val now = LocalDate.of(2026, 9, 14).atStartOfDay(zone).toInstant().toEpochMilli()
        val twoDaysAgo = LocalDate.of(2026, 9, 12).atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals("12 sep · 2 days ago", ResultText.saved(twoDaysAgo, now, zone))
    }

    @Test
    fun `counter formats design examples correctly`() {
        val today = LocalDate.of(2026, 10, 11)

        // Example from §17: "1 of 17 · internship · images · apr"
        val interp = Interpretation(
            query = "internship",
            types = listOf("IMAGE"),
            from = "2026-04-01",
            to = "2026-04-30",
        )
        val line = ResultText.counter(1, 17, "internship screenshot from april", interp, today)
        assertEquals("1 of 17 · internship · images · apr", line)

        // Without interpretation
        val lineWithout = ResultText.counter(3, 10, "internship", null, today)
        assertEquals("3 of 10 · internship", lineWithout)

        // With status
        val offlineLine = ResultText.counter(3, 10, "internship", null, today, status = SearchStatus.OFFLINE)
        assertEquals("3 of 10 · internship · offline · words only", offlineLine)

        val busyLine = ResultText.counter(3, 10, "internship", null, today, status = SearchStatus.UNAVAILABLE)
        assertEquals("3 of 10 · internship · search busy · words only", busyLine)
    }
}
