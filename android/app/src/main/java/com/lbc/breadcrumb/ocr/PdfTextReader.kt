package com.lbc.breadcrumb.ocr

import java.io.File

/**
 * Extracts readable text from a PDF file on the device.
 */
interface PdfTextReader {
    /**
     * Reads text from [file]. Returns recognized text trimmed (up to 20,000 characters),
     * or empty string if no text was found, the PDF is password-protected/encrypted,
     * or the file could not be read.
     */
    suspend fun readText(file: File): String
}
