package com.lbc.breadcrumb.voice

import android.media.MediaMetadataRetriever
import android.util.LruCache
import java.io.File
import java.util.Locale

/**
 * How long a sound file plays, in ms, read from its header once and then
 * remembered: a tile, the detail and the transcriber all ask. Null when the
 * file isn't sound the phone can read.
 */
object AudioLength {

    private val known = LruCache<String, Long>(256)

    fun of(file: File): Long? {
        val key = "${file.path}@${file.lastModified()}"
        known.get(key)?.let { return it }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?.also { known.put(key, it) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** "0:42", "12:05", "1:02:09". */
    fun clock(ms: Long): String {
        val seconds = (ms + 500) / 1000
        val h = seconds / 3600
        val m = seconds / 60 % 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
    }
}
