package com.lbc.breadcrumb.ocr

import java.io.File

/**
 * Extracts visible text from an image file on the device.
 */
interface ImageTextReader {
    /**
     * Reads text from [file]. Returns the recognized text trimmed, or empty string
     * if no text was found or the file could not be read.
     */
    suspend fun readText(file: File): String
}
