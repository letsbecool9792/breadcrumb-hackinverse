package com.lbc.breadcrumb.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class ExifDatesTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")

    @Test
    fun `a date with its offset is read at that offset`() {
        // 2026-04-12 18:30:05 at +05:30 is 13:00:05 UTC.
        assertEquals(1775998805000L, exifDateToMillis("2026:04:12 18:30:05", "+05:30", ZoneId.of("UTC")))
    }

    @Test
    fun `a date without an offset is read in the phone's zone`() {
        assertEquals(1775998805000L, exifDateToMillis("2026:04:12 18:30:05", null, kolkata))
    }

    @Test
    fun `an unreadable offset falls back to the phone's zone`() {
        assertEquals(1775998805000L, exifDateToMillis("2026:04:12 18:30:05", "local", kolkata))
    }

    @Test
    fun `a missing, blank or zeroed date is no date`() {
        assertNull(exifDateToMillis(null, null, kolkata))
        assertNull(exifDateToMillis("    :  :     :  :  ", null, kolkata))
        assertNull(exifDateToMillis("0000:00:00 00:00:00", null, kolkata))
        assertNull(exifDateToMillis("12/04/2026", null, kolkata))
    }
}
