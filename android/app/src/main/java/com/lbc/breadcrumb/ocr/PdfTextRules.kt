package com.lbc.breadcrumb.ocr

/**
 * Text extraction and selection rules for PDF reading (reading workstream R3).
 *
 * Requirements:
 * - Extract at most 20,000 characters.
 * - OCR only the first 6 pages when there is no text layer or on older Android.
 * - If a text layer exists but is very thin and OCR clearly provides substantially
 *   more text, use OCR instead (e.g. a scanned PDF with only a 1-line header).
 * - A genuine 1-line PDF whose text layer is accurate is kept rather than replaced.
 */
object PdfTextRules {
    const val MAX_PDF_TEXT_LENGTH = 20_000
    const val MAX_OCR_PAGES = 6
    const val THIN_TEXT_THRESHOLD = 200

    /**
     * Determines whether a text layer is thin enough to warrant checking OCR
     * for scanned content underneath.
     */
    fun isThinText(text: String): Boolean =
        text.isBlank() || text.length < THIN_TEXT_THRESHOLD

    /**
     * Selects the best text between an extracted text layer and OCR results.
     * Capped at [MAX_PDF_TEXT_LENGTH] characters.
     */
    fun selectBestText(textLayer: String?, ocrText: String?): String {
        val cleanTextLayer = textLayer?.trim().orEmpty()
        val cleanOcr = ocrText?.trim().orEmpty()

        if (cleanTextLayer.isEmpty()) {
            return cleanOcr.take(MAX_PDF_TEXT_LENGTH)
        }

        if (!isThinText(cleanTextLayer)) {
            return cleanTextLayer.take(MAX_PDF_TEXT_LENGTH)
        }

        // The text layer is thin (e.g. under 200 chars).
        // Check if OCR found clearly and substantially more text:
        // either OCR is more than 100 characters longer, or at least double the length.
        val ocrFoundClearlyMore = cleanOcr.length > cleanTextLayer.length + 100 ||
            (cleanOcr.length >= cleanTextLayer.length * 2 && cleanOcr.length > cleanTextLayer.length + 20)

        val selected = if (ocrFoundClearlyMore) cleanOcr else cleanTextLayer
        return selected.take(MAX_PDF_TEXT_LENGTH)
    }
}
