package com.lbc.breadcrumb.capture

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface

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

    fun getDateTaken(context: Context, uri: Uri): Long? {
        var date: Long? = null
        try {
            // Try MediaStore first
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                    if (idx >= 0) {
                        date = cursor.getLong(idx)
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        
        if (date == null || date == 0L) {
            try {
                // Try EXIF
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val exif = ExifInterface(input)
                    val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    // EXIF format is "YYYY:MM:DD HH:MM:SS"
                    // Let's just return null if it's too complex to parse right now, 
                    // or parse it using SimpleDateFormat.
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        return if (date != 0L) date else null
    }
}
