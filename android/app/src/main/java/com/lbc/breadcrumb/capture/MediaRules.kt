package com.lbc.breadcrumb.capture

import com.lbc.breadcrumb.data.MemoryType
import java.util.Locale

/** How a shared or copied file becomes a memory. Pure, so JVM tests cover it. */
internal object MediaRules {

    /** The provider's type, else the type the intent (or the clip) declared. */
    fun mimeOf(providerType: String?, declaredType: String?): String? =
        providerType?.takeIf { it.isNotBlank() } ?: declaredType?.takeIf { it.isNotBlank() }

    /** IMAGE for any image, PDF for a PDF, null for anything else: it is skipped. */
    fun kindOf(mime: String?): MemoryType? {
        val type = mime?.lowercase(Locale.ROOT) ?: return null
        return when {
            type == "application/pdf" -> MemoryType.PDF
            type.startsWith("image/") -> MemoryType.IMAGE
            else -> null
        }
    }

    /**
     * The stored file's extension, from a fixed map, never from anything the
     * sender wrote. An image of a type the map lacks, or of no named type at
     * all, is stored as jpg, the likeliest.
     */
    fun extensionFor(mime: String?): String =
        EXTENSIONS[mime?.lowercase(Locale.ROOT)] ?: if (kindOf(mime) == MemoryType.PDF) "pdf" else "jpg"

    /** Shared text rides on a file only when exactly one came: with several, it is no one file's. */
    fun captionFor(text: String?, fileCount: Int): String? =
        text?.takeIf { it.isNotBlank() && fileCount == 1 }

    /**
     * A file's title: the share's subject, unless it is generic or just the
     * file's name; else, for a PDF, its file name without ".pdf". A picture
     * without a real subject has none: its words come from OCR.
     */
    fun titleFor(subject: String?, fileName: String?, kind: MemoryType): String? {
        val name = fileName?.trim()?.takeIf { it.isNotEmpty() }
        val trimmed = subject?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmed != null && !TextRules.isGenericSubject(trimmed) && !isFileName(trimmed, name)) return trimmed
        return if (kind == MemoryType.PDF) name?.let(::withoutPdf) else null
    }

    /**
     * When the file's content was made, in epoch ms: the provider's DATE_TAKEN
     * (ms), else MediaStore's DATE_MODIFIED (seconds), else a document's
     * COLUMN_LAST_MODIFIED (ms). Null when none is known; the caller then
     * reads the stored copy's EXIF.
     */
    fun contentDate(dateTaken: Long?, dateModifiedSeconds: Long?, lastModified: Long?): Long? =
        dateTaken?.takeIf { it > 0 }
            ?: dateModifiedSeconds?.takeIf { it > 0 }?.times(1000)
            ?: lastModified?.takeIf { it > 0 }

    private fun isFileName(subject: String, name: String?): Boolean =
        name != null && (subject.equals(name, ignoreCase = true) ||
            subject.equals(name.substringBeforeLast('.'), ignoreCase = true))

    private fun withoutPdf(name: String): String =
        if (name.endsWith(".pdf", ignoreCase = true)) name.dropLast(4) else name

    private val EXTENSIONS = mapOf(
        "image/jpeg" to "jpg", "image/jpg" to "jpg", "image/pjpeg" to "jpg",
        "image/png" to "png", "image/webp" to "webp", "image/gif" to "gif",
        "image/heic" to "heic", "image/heif" to "heif", "image/avif" to "avif",
        "image/bmp" to "bmp", "image/x-ms-bmp" to "bmp",
        "application/pdf" to "pdf",
    )
}
