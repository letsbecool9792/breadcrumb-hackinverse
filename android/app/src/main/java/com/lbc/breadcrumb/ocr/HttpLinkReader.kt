package com.lbc.breadcrumb.ocr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Reads a web link using OkHttp on [Dispatchers.IO].
 *
 * Enforces the R4 reading rules:
 * - Read only the HTML <head>.
 * - Stop at </head> OR after 512 KB, whichever comes first.
 * - Enforces http -> https and blocks localhost / bare IP addresses.
 * - Extracts <title> and meta description via [LinkRules].
 * - Network or read errors return [LinkReadResult.EMPTY].
 */
class HttpLinkReader(
    private val client: OkHttpClient = defaultHttpClient(),
    private val allowLocalhostForTesting: Boolean = false,
) : LinkReader {

    override suspend fun readLink(url: String): LinkReadResult = withContext(Dispatchers.IO) {
        val normalized = if (allowLocalhostForTesting) {
            url
        } else {
            LinkRules.normalizeUrl(url) ?: return@withContext LinkReadResult.EMPTY
        }

        if (!allowLocalhostForTesting && !LinkRules.isAllowedUrl(normalized)) {
            return@withContext LinkReadResult.EMPTY
        }

        try {
            val request = Request.Builder()
                .url(normalized)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext LinkReadResult.EMPTY
                }
                val body = response.body ?: return@withContext LinkReadResult.EMPTY
                val contentType = response.header("Content-Type")?.lowercase()
                if (contentType != null &&
                    !contentType.contains("text/html") &&
                    !contentType.contains("application/xhtml+xml")
                ) {
                    return@withContext LinkReadResult.EMPTY
                }

                val charset = body.contentType()?.charset() ?: Charsets.UTF_8
                val outputStream = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var totalRead = 0L

                val inputStream = body.byteStream()
                while (totalRead < MAX_BYTES) {
                    val toRead = minOf(buffer.size.toLong(), MAX_BYTES - totalRead).toInt()
                    val read = inputStream.read(buffer, 0, toRead)
                    if (read == -1) break
                    outputStream.write(buffer, 0, read)
                    totalRead += read

                    // Stop early as soon as </head> is seen
                    val currentHtml = outputStream.toString(charset.name())
                    if (currentHtml.contains("</head>", ignoreCase = true)) {
                        break
                    }
                }

                val html = outputStream.toString(charset.name())
                val head = LinkRules.extractHeadContent(html)
                val title = LinkRules.extractTitle(head)
                val description = LinkRules.extractDescription(head)

                LinkReadResult(
                    title = title,
                    description = description,
                )
            }
        } catch (_: IOException) {
            LinkReadResult.EMPTY
        } catch (_: IllegalArgumentException) {
            LinkReadResult.EMPTY
        } catch (_: SecurityException) {
            LinkReadResult.EMPTY
        }
    }

    companion object {
        const val MAX_BYTES = 512L * 1024L // 512 KB
        const val USER_AGENT = "Mozilla/5.0 (Android; Mobile; rv:124.0) Gecko/124.0 Firefox/124.0"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val requestUrl = chain.request().url.toString()
                if (!LinkRules.isAllowedUrl(requestUrl)) {
                    throw IOException("Target URL is not allowed: $requestUrl")
                }
                chain.proceed(chain.request())
            }
            .build()
    }
}
