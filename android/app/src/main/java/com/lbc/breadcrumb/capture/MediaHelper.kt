package com.lbc.breadcrumb.capture

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.lbc.breadcrumb.data.MemoryType
import java.io.File

object MediaHelper {
    fun getDisplayName(context: Context, uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        name = cursor.getString(idx)
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return name
    }

    /**
     * When a shared file's content was made, read at capture (later, the
     * provider may be gone): what the provider says, in [MediaRules.contentDate]'s
     * order, else, for a picture, the EXIF of the copy already [stored], read from
     * our own file rather than a second trip to the provider.
     */
    fun contentDate(context: Context, uri: Uri, stored: File, kind: MemoryType): Long? {
        var taken: Long? = null
        var modifiedSeconds: Long? = null
        var lastModified: Long? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    taken = cursor.longOrNull(MediaStore.MediaColumns.DATE_TAKEN)
                    modifiedSeconds = cursor.longOrNull(MediaStore.MediaColumns.DATE_MODIFIED)
                    lastModified = cursor.longOrNull(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                }
            }
        } catch (e: Exception) {
            // ignore: the stored copy may still know
        }
        return MediaRules.contentDate(taken, modifiedSeconds, lastModified)
            ?: if (kind == MemoryType.IMAGE) exifDate(stored) else null
    }

    private fun exifDate(file: File): Long? =
        try {
            val exif = ExifInterface(file.absolutePath)
            exifDateToMillis(
                exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME),
                // TAG_OFFSET_TIME_ORIGINAL, by name: the exifinterface this compiles
                // against (1.0.0, through ML Kit) predates the constant, while the
                // one that runs (1.4.1) reads the tag. An older one answers null.
                exif.getAttribute("OffsetTimeOriginal"),
            )
        } catch (e: Exception) {
            null
        }

    private fun Cursor.longOrNull(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }
}
