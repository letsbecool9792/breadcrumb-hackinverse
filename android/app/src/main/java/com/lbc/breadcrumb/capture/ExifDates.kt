package com.lbc.breadcrumb.capture

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * When a photo was taken, in epoch milliseconds, from its EXIF date
 * ("2026:04:12 18:30:05") and, when the camera wrote one, its UTC offset
 * ("+05:30"). Without an offset the date is read in [zone], the phone's own:
 * EXIF dates carry no zone, and the photo was most likely taken where the
 * phone is now. Null when the date is missing or malformed, including the
 * blanks and zeros some cameras write when their clock was never set.
 */
internal fun exifDateToMillis(
    dateTime: String?,
    offset: String?,
    zone: ZoneId = ZoneId.systemDefault(),
): Long? {
    val local = try {
        LocalDateTime.parse(dateTime?.trim() ?: return null, EXIF_DATE)
    } catch (_: Exception) {
        return null
    }
    val fixed = offset?.trim()?.let { runCatching { ZoneOffset.of(it) }.getOrNull() }
    val instant = if (fixed != null) local.toInstant(fixed) else local.atZone(zone).toInstant()
    return instant.toEpochMilli()
}

private val EXIF_DATE = DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss")
