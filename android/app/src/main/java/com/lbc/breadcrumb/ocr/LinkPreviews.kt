package com.lbc.breadcrumb.ocr

import android.content.Context
import com.lbc.breadcrumb.BreadcrumbApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * A link's preview picture (its page's og:image, or a video's thumbnail),
 * fetched once and kept in the originals folder, so a link's tile shows it
 * offline too. Fetched under the same rules as pages: https, no localhost,
 * no bare IPs, and at most 3 MB.
 */
class LinkPreviews(
    private val context: Context,
    private val client: OkHttpClient = HttpLinkReader.defaultHttpClient(),
) {
    private val app get() = context.applicationContext as BreadcrumbApp

    /** Saves [imageUrl] as [memoryId]'s preview. True when it is kept. */
    suspend fun save(memoryId: String, imageUrl: String): Boolean = withContext(Dispatchers.IO) {
        val url = LinkRules.normalizeUrl(imageUrl)?.takeIf(LinkRules::isAllowedUrl) ?: return@withContext false
        val temp = File(context.cacheDir, "preview-$memoryId.part")
        try {
            val extension = client.newCall(Request.Builder().url(url).header("User-Agent", HttpLinkReader.USER_AGENT).build())
                .execute().use { response ->
                    val type = response.header("Content-Type")?.lowercase().orEmpty()
                    if (!response.isSuccessful || !type.startsWith("image/")) return@withContext false
                    temp.outputStream().use { out ->
                        val input = response.body.byteStream()
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_BYTES) return@withContext false
                            out.write(buffer, 0, read)
                        }
                    }
                    when {
                        "png" in type -> "png"
                        "webp" in type -> "webp"
                        else -> "jpg"
                    }
                }
            val file = app.originals.moveIn(temp, memoryId, extension)
            if (app.database.readingDao().setLinkPreview(memoryId, app.originals.uriFor(file)) == 0) file.delete()
            true
        } catch (_: Exception) {
            false
        } finally {
            temp.delete()
        }
    }

    /**
     * Links with no picture: each is read again once, for its picture and,
     * where it had none or only the site's name, its title. Once per [TRIED]:
     * a change to how links are read gives every one of them another try.
     */
    suspend fun fillIn(reader: LinkReader = HttpLinkReader()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tried = prefs.getStringSet(TRIED, emptySet()).orEmpty()
        val dao = app.database.readingDao()
        for (memory in dao.linksWithoutPreview().filter { it.id !in tried }) {
            val url = memory.rawText?.let { com.lbc.breadcrumb.capture.TextRules.firstUrl(it) } ?: continue
            val read = try {
                reader.readLink(url)
            } catch (_: java.io.IOException) {
                return // offline: the rest wait for the next pass
            }
            read.title?.let { dao.fillLinkTitle(memory.id, it, read.description, bare = LinkRules.siteName(url).orEmpty()) }
            read.imageUrl?.let { save(memory.id, it) }
            prefs.edit().putStringSet(TRIED, prefs.getStringSet(TRIED, emptySet()).orEmpty() + memory.id).apply()
        }
    }

    private companion object {
        const val MAX_BYTES = 3L * 1024 * 1024
        const val PREFS = "link_previews"
        // "tried" before walled pages were asked again as Breadcrumb (Instagram).
        const val TRIED = "tried_app_agent"
    }
}
