package com.lbc.breadcrumb.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextRulesTest {

    @Test
    fun `substantial text layer is selected directly`() {
        val substantialText = "A".repeat(300)
        val ocrText = "B".repeat(500)

        val result = PdfTextRules.selectBestText(substantialText, ocrText)
        assertEquals(substantialText, result)
        assertFalse(PdfTextRules.isThinText(substantialText))
    }

    @Test
    fun `text layer is capped at 20000 characters`() {
        val longText = "X".repeat(25000)
        val result = PdfTextRules.selectBestText(longText, null)

        assertEquals(20000, result.length)
        assertEquals("X".repeat(20000), result)
    }

    @Test
    fun `empty or null text layer falls back to ocr`() {
        val ocrText = "Recognized OCR text from first six pages"
        val resultEmpty = PdfTextRules.selectBestText("", ocrText)
        val resultBlank = PdfTextRules.selectBestText("   ", ocrText)
        val resultNull = PdfTextRules.selectBestText(null, ocrText)

        assertEquals(ocrText, resultEmpty)
        assertEquals(ocrText, resultBlank)
        assertEquals(ocrText, resultNull)
    }

    @Test
    fun `ocr text is capped at 20000 characters`() {
        val longOcr = "Y".repeat(25000)
        val result = PdfTextRules.selectBestText(null, longOcr)

        assertEquals(20000, result.length)
        assertEquals("Y".repeat(20000), result)
    }

    @Test
    fun `thin text layer is replaced when ocr finds substantially more text`() {
        // e.g. Scanned document with only a 1-line header in the text layer
        val thinTextLayer = "Scanned with Mobile Scanner"
        val fullOcrText = "Contract Agreement. Paragraph 1: Terms and conditions. Paragraph 2: Details." + "A".repeat(150)

        assertTrue(PdfTextRules.isThinText(thinTextLayer))
        val result = PdfTextRules.selectBestText(thinTextLayer, fullOcrText)
        assertEquals(fullOcrText, result)
    }

    @Test
    fun `thin text layer is kept when ocr does not find clearly more text`() {
        // A genuine one-line PDF invoice or note
        val genuineOneLine = "Invoice #1042 total: $450 paid in full"
        val similarOcr = "Invoice #1042 total: $450 paid"

        assertTrue(PdfTextRules.isThinText(genuineOneLine))
        val result = PdfTextRules.selectBestText(genuineOneLine, similarOcr)
        assertEquals(genuineOneLine, result)
    }

    @Test
    fun `returns empty string when both text layer and ocr are empty or null`() {
        assertEquals("", PdfTextRules.selectBestText(null, null))
        assertEquals("", PdfTextRules.selectBestText("", ""))
        assertEquals("", PdfTextRules.selectBestText("   ", "   "))
    }
}
