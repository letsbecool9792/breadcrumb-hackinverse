package com.lbc.breadcrumb.net

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RedactorTest {

    @Test
    fun `preserves urls intact`() {
        val url = "https://www.linkedin.com/jobs/view/4120938817"
        assertEquals(url, Redactor.redact(url))

        val sentence = "Check https://github.com/letsbecool9792/breadcrumb-hackinverse for instructions"
        assertEquals(sentence, Redactor.redact(sentence))
    }

    @Test
    fun `preserves dates and times intact`() {
        val sample1 = "Apply by April 30. Bengaluru."
        assertEquals(sample1, Redactor.redact(sample1))

        val sample2 = "Event scheduled for 2026-10-11 at 10:30 AM"
        assertEquals(sample2, Redactor.redact(sample2))

        val sample3 = "Submitted on 15/08/2026"
        assertEquals(sample3, Redactor.redact(sample3))
    }

    @Test
    fun `preserves normal job posting and notes text intact`() {
        val title = "Software Engineering Intern, Qualcomm"
        val text = "Requirements: Kotlin, C++, algorithms, data structures"
        assertEquals(title, Redactor.redact(title))
        assertEquals(text, Redactor.redact(text))
    }

    @Test
    fun `redacts phone numbers`() {
        assertEquals("Call Arjun at [phone]", Redactor.redact("Call Arjun at +91 98450 12345"))
        assertEquals("Mobile: [phone]", Redactor.redact("Mobile: 9876543210"))
        assertEquals("Call [phone] for help", Redactor.redact("Call +1 555-123-4567 for help"))
    }

    @Test
    fun `redacts email addresses`() {
        assertEquals("Contact [email] for details", Redactor.redact("Contact priya.sharma@example.com for details"))
        assertEquals("Email: [email]", Redactor.redact("Email: dev-support_2026@sub.domain.co.in"))
    }

    @Test
    fun `redacts payment card numbers`() {
        assertEquals("Paid with card [card]", Redactor.redact("Paid with card 4111 2222 3333 4444"))
        assertEquals("Card: [card]", Redactor.redact("Card: 5500-1234-5678-9012"))
        assertEquals("Card [card] on file", Redactor.redact("Card 4111222233334444 on file"))
    }

    @Test
    fun `redacts ibans`() {
        assertEquals("Wire to [iban] please", Redactor.redact("Wire to GB82 WEST 1234 5698 7654 32 please"))
        assertEquals("IBAN: [iban]", Redactor.redact("IBAN: DE89370400440532013000"))
    }

    @Test
    fun `redacts tracking numbers`() {
        assertEquals("UPS package [tracking] in transit", Redactor.redact("UPS package 1Z9999999999999999 in transit"))
        assertEquals("Speed Post [tracking] out for delivery", Redactor.redact("Speed Post EM123456789IN out for delivery"))
    }

    @Test
    fun `redacts otps and verification codes`() {
        assertEquals("Your OTP is [otp]", Redactor.redact("Your OTP is 482910"))
        assertEquals("Verification code: [otp]", Redactor.redact("Verification code: 123456"))
        assertEquals("[otp] is your one-time password", Redactor.redact("592019 is your one-time password"))
        assertEquals("Use [otp] to verify your account", Redactor.redact("Use 4821 to verify your account"))
    }

    @Test
    fun `redacts aadhaar numbers with and without spaces`() {
        assertEquals("Aadhaar: [aadhaar]", Redactor.redact("Aadhaar: 2345 6789 0123"))
        assertEquals("Aadhaar: [aadhaar]", Redactor.redact("Aadhaar: 234567890123"))
        assertEquals("ID: [aadhaar] verified", Redactor.redact("ID: 2345-6789-0123 verified"))
    }

    @Test
    fun `redacts pan numbers`() {
        assertEquals("PAN [pan] verified", Redactor.redact("PAN ABCDE1234F verified"))
        assertEquals("Tax ID: [pan]", Redactor.redact("Tax ID: btcpa4321k"))
    }

    @Test
    fun `redacts api keys and long tokens`() {
        assertEquals("OpenAI key: [key]", Redactor.redact("OpenAI key: sk-proj-abc1234567890def12345678"))
        assertEquals("Google key: [key]", Redactor.redact("Google key: AIzaSyD-1234567890abcdefghij-klmnopqr"))
        assertEquals("GitHub token: [key]", Redactor.redact("GitHub token: ghp_1234567890abcdefghijklmnopqrstuvwxyz"))
        assertEquals("AWS: [key]", Redactor.redact("AWS: AKIAIOSFODNN7EXAMPLE"))
        assertEquals("Token: [key]", Redactor.redact("Token: 3b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e"))
    }

    @Test
    fun `redacts addresses via entity spans`() {
        val text = "Office located at Bagmane Tech Park, CV Raman Nagar, Bengaluru on April 30."
        val spans = listOf(RedactionSpan(18, 62, "[address]"))
        val redacted = Redactor.redact(text, spans)
        assertEquals("Office located at [address] on April 30.", redacted)
    }

    @Test
    fun `Memory toUpload redacts text fields without modifying local copy`() {
        val original = Memory(
            id = "mem-1",
            type = MemoryType.TEXT,
            title = "Qualcomm chat with +91 98450 12345",
            rawText = "PAN is ABCDE1234F and OTP is 987654",
            extractedText = "Reached via https://example.com/job on April 30, Aadhaar: 2345 6789 0123",
            note = "Secret key: sk-proj-1234567890abcdef12345678",
        )

        val upload = original.toUpload()!!

        // Verify upload has redacted fields
        assertEquals("Qualcomm chat with [phone]", upload.title)
        assertEquals("PAN is [pan] and OTP is [otp]", upload.rawText)
        assertEquals("Reached via https://example.com/job on April 30, Aadhaar: [aadhaar]", upload.extractedText)
        assertEquals("Secret key: [key]", upload.note)

        // Verify local Memory copy is completely untouched
        assertEquals("Qualcomm chat with +91 98450 12345", original.title)
        assertEquals("PAN is ABCDE1234F and OTP is 987654", original.rawText)
        assertEquals("Reached via https://example.com/job on April 30, Aadhaar: 2345 6789 0123", original.extractedText)
        assertEquals("Secret key: sk-proj-1234567890abcdef12345678", original.note)
    }

    @Test
    fun `for upload, text the entity model could not read does not go`() {
        assertNull(Redactor.forUpload("Call me at home", extractor = null))
        assertNull(Redactor.forUpload("Call me at home") { null })
        assertNull(Redactor.forUpload("Call me at home") { error("timed out") })
    }

    @Test
    fun `for upload, the model's spans and the rules both strip`() {
        val text = "Office at Bagmane Tech Park, Bengaluru; PAN ABCDE1234F"
        val stripped = Redactor.forUpload(text) { listOf(RedactionSpan(10, 38, "[address]")) }
        assertEquals("Office at [address]; PAN [pan]", stripped)
    }

    @Test
    fun `long text is read in pieces cut at whitespace, and each piece's spans land at the right offsets`() {
        val text = "aaaa bbbb cccc dddd"
        assertEquals(listOf(0 to "aaaa bbbb ", 10 to "cccc dddd"), EntityChunks.pieces(text, size = 12))

        // A fake model that marks every "cccc" in the piece it is given.
        val spans = EntityChunks.annotate(text, size = 12) { piece ->
            val at = piece.indexOf("cccc")
            if (at < 0) emptyList() else listOf(RedactionSpan(at, at + 4, "[x]"))
        }
        assertEquals(listOf(RedactionSpan(10, 14, "[x]")), spans)
        assertEquals("aaaa bbbb [x] dddd", Redactor.redact(text, spans!!))
    }

    @Test
    fun `one piece that could not be read means none of the text goes`() {
        val text = "a".repeat(30)
        var pieces = 0
        assertNull(EntityChunks.annotate(text, size = 10) { pieces++; if (pieces == 2) null else emptyList() })
    }
}
