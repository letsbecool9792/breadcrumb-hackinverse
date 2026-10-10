package com.lbc.breadcrumb.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class FileMetadataTest {

    @Test
    fun `parses screenshot datetime format with hyphen or underscore`() {
        val timestamp1 = FileMetadata.parseFilenameDate("Screenshot_20240315-103045_Chrome.jpg")
        assertNotNull(timestamp1)

        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = timestamp1!! }
        assertEquals(2024, cal.get(Calendar.YEAR))
        assertEquals(3, cal.get(Calendar.MONTH) + 1)
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
        assertEquals(45, cal.get(Calendar.SECOND))

        val timestamp2 = FileMetadata.parseFilenameDate("Screenshot_2024-03-15-10-30-45.png")
        assertNotNull(timestamp2)
        assertEquals(timestamp1, timestamp2)
    }

    @Test
    fun `parses camera photo datetime format`() {
        val timestamp = FileMetadata.parseFilenameDate("IMG_20240315_103045.jpg")
        assertNotNull(timestamp)

        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = timestamp!! }
        assertEquals(2024, cal.get(Calendar.YEAR))
        assertEquals(3, cal.get(Calendar.MONTH) + 1)
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
        assertEquals(45, cal.get(Calendar.SECOND))
    }

    @Test
    fun `parses date-only format with fallback to midnight`() {
        val timestamp = FileMetadata.parseFilenameDate("Screenshot_2024-03-15.png")
        assertNotNull(timestamp)

        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = timestamp!! }
        assertEquals(2024, cal.get(Calendar.YEAR))
        assertEquals(3, cal.get(Calendar.MONTH) + 1)
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
    }

    @Test
    fun `returns null for non-date filenames`() {
        assertNull(FileMetadata.parseFilenameDate("document.pdf"))
        assertNull(FileMetadata.parseFilenameDate("random_flyer.png"))
        assertNull(FileMetadata.parseFilenameDate("resume_final_v2.pdf"))
    }

    @Test
    fun `parses exif date string correctly`() {
        val parsed = FileMetadata.parseExifDateString("2024:03:15 10:30:45")
        assertNotNull(parsed)

        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = parsed!! }
        assertEquals(2024, cal.get(Calendar.YEAR))
        assertEquals(3, cal.get(Calendar.MONTH) + 1)
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `a zeroed or impossible exif date is no date`() {
        assertNull(FileMetadata.parseExifDateString("0000:00:00 00:00:00"))
        assertNull(FileMetadata.parseExifDateString("2024:13:45 10:30:45"))
        assertNull(FileMetadata.parseExifDateString("    :  :     :  :  "))
    }

    @Test
    fun `respects timestamp resolution priority exif then filename then provider`() {
        val exifTime = 1000L
        val filename = "Screenshot_20240315-103045.png"
        val providerTime = 3000L

        // 1. When EXIF is present, it wins over filename and provider
        val resolvedWithExif = FileMetadata.resolveContentCreatedAt(
            exifDate = exifTime,
            filename = filename,
            providerLastModified = providerTime,
        )
        assertEquals(exifTime, resolvedWithExif)

        // 2. When EXIF is null, filename date wins over provider
        val resolvedWithFilename = FileMetadata.resolveContentCreatedAt(
            exifDate = null,
            filename = filename,
            providerLastModified = providerTime,
        )
        val expectedFileDate = FileMetadata.parseFilenameDate(filename)
        assertEquals(expectedFileDate, resolvedWithFilename)

        // 3. When neither EXIF nor filename has date, provider lastModified wins
        val resolvedWithProvider = FileMetadata.resolveContentCreatedAt(
            exifDate = null,
            filename = "document.pdf",
            providerLastModified = providerTime,
        )
        assertEquals(providerTime, resolvedWithProvider)

        // 4. When nothing is available, returns null
        val resolvedEmpty = FileMetadata.resolveContentCreatedAt(
            exifDate = null,
            filename = "document.pdf",
            providerLastModified = null,
        )
        assertNull(resolvedEmpty)
    }

    @Test
    fun `resolves folder label correctly`() {
        assertEquals("Screenshots", FileMetadata.resolveFolderLabel("Screenshots"))
        assertEquals("Screenshots", FileMetadata.resolveFolderLabel("Pictures/Screenshots"))
        assertEquals("Camera", FileMetadata.resolveFolderLabel("DCIM"))
        assertEquals("Camera", FileMetadata.resolveFolderLabel("Camera"))
        assertEquals("Downloads", FileMetadata.resolveFolderLabel("Download"))
        assertEquals("Photos", FileMetadata.resolveFolderLabel(""))
    }
}
