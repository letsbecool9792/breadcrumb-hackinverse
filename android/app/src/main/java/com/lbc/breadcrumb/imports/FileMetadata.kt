package com.lbc.breadcrumb.imports

import android.media.ExifInterface
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

object FileMetadata {

    private val EXIF_DATE_FORMAT = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }
    private val EXIF_ALT_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    // Matches year, month, day, separator, hour, min, sec:
    // e.g. Screenshot_20240315-103045, Screenshot_2024-03-15-10-30-45, IMG_20240315_103045, 20240315_103045
    private val DATETIME_PATTERN = Pattern.compile(
        "(?:^|[^0-9])(\\d{4})[-_]?(\\d{2})[-_]?(\\d{2})[-_T ](\\d{2})[-_.:]?(\\d{2})[-_.:]?(\\d{2})(?:[^0-9]|$)",
        Pattern.CASE_INSENSITIVE
    )

    // Matches year-month-day separated: e.g. Screenshot_2024-03-15
    private val DATE_HYPHEN_PATTERN = Pattern.compile(
        "(?:^|[^0-9])(\\d{4})[-_](\\d{2})[-_](\\d{2})(?:[^0-9]|$)",
        Pattern.CASE_INSENSITIVE
    )

    // Matches compact yearmonthday with prefix: e.g. Screenshot_20240315, IMG_20240315
    private val DATE_COMPACT_PATTERN = Pattern.compile(
        "(?:screenshot|img|pxl|vid)[-_]?(\\d{4})(\\d{2})(\\d{2})(?:[^0-9]|$)",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Resolves contentCreatedAt according to priority:
     * 1. EXIF date when available
     * 2. Filename-derived date
     * 3. Provider metadata (e.g. COLUMN_LAST_MODIFIED)
     */
    fun resolveContentCreatedAt(
        exifDate: Long?,
        filename: String,
        providerLastModified: Long?,
    ): Long? =
        exifDate
            ?: parseFilenameDate(filename)
            ?: providerLastModified?.takeIf { it > 0L }

    /**
     * Attempts to read the original creation timestamp from EXIF metadata.
     */
    fun readExifDate(inputStream: InputStream): Long? {
        return try {
            val exif = ExifInterface(inputStream)
            val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            dateStr?.let { parseExifDateString(it) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Attempts to read the original creation timestamp from EXIF metadata of a [File].
     */
    fun readExifDate(file: File): Long? {
        return try {
            val exif = ExifInterface(file)
            val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            dateStr?.let { parseExifDateString(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun parseExifDateString(dateString: String): Long? {
        val trimmed = dateString.trim()
        return try {
            synchronized(EXIF_DATE_FORMAT) {
                EXIF_DATE_FORMAT.parse(trimmed)?.time
            }
        } catch (_: Exception) {
            try {
                synchronized(EXIF_ALT_FORMAT) {
                    EXIF_ALT_FORMAT.parse(trimmed)?.time
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Extracts a timestamp from common Android camera/screenshot filename patterns.
     */
    fun parseFilenameDate(filename: String): Long? {
        // Try date + time first
        val dtMatcher = DATETIME_PATTERN.matcher(filename)
        if (dtMatcher.find()) {
            val year = dtMatcher.group(1)?.toIntOrNull() ?: return null
            val month = dtMatcher.group(2)?.toIntOrNull() ?: return null
            val day = dtMatcher.group(3)?.toIntOrNull() ?: return null
            val hour = dtMatcher.group(4)?.toIntOrNull() ?: return null
            val min = dtMatcher.group(5)?.toIntOrNull() ?: return null
            val sec = dtMatcher.group(6)?.toIntOrNull() ?: return null

            if (isValidDateTime(year, month, day, hour, min, sec)) {
                return buildCalendarTime(year, month, day, hour, min, sec)
            }
        }

        // Try date with hyphens/underscores
        val dhMatcher = DATE_HYPHEN_PATTERN.matcher(filename)
        if (dhMatcher.find()) {
            val year = dhMatcher.group(1)?.toIntOrNull() ?: return null
            val month = dhMatcher.group(2)?.toIntOrNull() ?: return null
            val day = dhMatcher.group(3)?.toIntOrNull() ?: return null

            if (isValidDate(year, month, day)) {
                return buildCalendarTime(year, month, day, 0, 0, 0)
            }
        }

        // Try compact date with prefix
        val dcMatcher = DATE_COMPACT_PATTERN.matcher(filename)
        if (dcMatcher.find()) {
            val year = dcMatcher.group(1)?.toIntOrNull() ?: return null
            val month = dcMatcher.group(2)?.toIntOrNull() ?: return null
            val day = dcMatcher.group(3)?.toIntOrNull() ?: return null

            if (isValidDate(year, month, day)) {
                return buildCalendarTime(year, month, day, 0, 0, 0)
            }
        }

        return null
    }

    private fun isValidDate(year: Int, month: Int, day: Int): Boolean =
        year in 1990..2100 && month in 1..12 && day in 1..31

    private fun isValidDateTime(year: Int, month: Int, day: Int, hour: Int, min: Int, sec: Int): Boolean =
        isValidDate(year, month, day) && hour in 0..23 && min in 0..59 && sec in 0..59

    private fun buildCalendarTime(year: Int, month: Int, day: Int, hour: Int, min: Int, sec: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1) // Calendar months are 0-based
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, min)
        cal.set(Calendar.SECOND, sec)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Resolves human-readable source label from a folder name.
     */
    fun resolveFolderLabel(folderName: String): String {
        val lower = folderName.lowercase()
        return when {
            lower.contains("screenshot") -> "Screenshots"
            lower.contains("camera") || lower.contains("dcim") -> "Camera"
            lower.contains("download") -> "Downloads"
            lower.isBlank() -> "Photos"
            else -> folderName.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
