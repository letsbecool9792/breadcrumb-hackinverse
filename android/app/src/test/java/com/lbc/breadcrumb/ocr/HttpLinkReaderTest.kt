package com.lbc.breadcrumb.ocr

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class HttpLinkReaderTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun stopsReadingAtClosingHeadTag() = runBlocking {
        // Prepare HTML with title and description, followed by large body
        val headContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Stopping At Head Title</title>
                <meta name="description" content="Extracted description from head.">
            </head>
        """.trimIndent()

        // 100 KB body after </head>
        val largeBody = "<body>" + "A".repeat(100 * 1024) + "</body></html>"
        val fullHtml = headContent + largeBody

        server.enqueue(
            MockResponse(
                code = 200,
                headers = okhttp3.Headers.headersOf("Content-Type", "text/html; charset=utf-8"),
                body = fullHtml,
            ),
        )

        val reader = HttpLinkReader(client = client, allowLocalhostForTesting = true)
        val result = reader.readLink(server.url("/test").toString())

        assertEquals("Stopping At Head Title", result.title)
        assertEquals("Extracted description from head.", result.description)
    }

    @Test
    fun stopsReadingAt512KbLimitWhenNoHeadTag() = runBlocking {
        // 600 KB of HTML without closing </head>
        val titlePart = "<title>Truncated Page</title><meta name=\"description\" content=\"Short description\">"
        val padding = "X".repeat(600 * 1024)
        val fullHtml = "<html><head>$titlePart$padding"

        server.enqueue(
            MockResponse(
                code = 200,
                headers = okhttp3.Headers.headersOf("Content-Type", "text/html; charset=utf-8"),
                body = fullHtml,
            ),
        )

        val reader = HttpLinkReader(client = client, allowLocalhostForTesting = true)
        val result = reader.readLink(server.url("/large").toString())

        // Even though stream was 600 KB, it successfully read title & description within the first 512 KB
        assertEquals("Truncated Page", result.title)
        assertEquals("Short description", result.description)
    }

    @Test
    fun http404ReturnsEmptyResult() = runBlocking {
        server.enqueue(MockResponse(code = 404))

        val reader = HttpLinkReader(client = client, allowLocalhostForTesting = true)
        val result = reader.readLink(server.url("/notfound").toString())

        assertNull(result.title)
        assertEquals("", result.description)
    }

    @Test
    fun http500ReturnsEmptyResult() = runBlocking {
        server.enqueue(MockResponse(code = 500))

        val reader = HttpLinkReader(client = client, allowLocalhostForTesting = true)
        val result = reader.readLink(server.url("/servererror").toString())

        assertNull(result.title)
        assertEquals("", result.description)
    }

    @Test
    fun aNetworkFailureThrowsSoLinkReadingCanTellOfflineFromDead() {
        // Close server immediately so request fails with network error
        val port = server.port
        server.close()

        val reader = HttpLinkReader(client = client, allowLocalhostForTesting = true)
        assertThrows(IOException::class.java) {
            runBlocking { reader.readLink("http://127.0.0.1:$port/failed") }
        }
    }

    @Test
    fun aRedirectToLocalhostIsRefusedAtTheHop() {
        // A public-looking host that resolves to this machine, as a real site would to its own server.
        val client = HttpLinkReader.defaultHttpClient().newBuilder()
            .dns { listOf(InetAddress.getByName("127.0.0.1")) }
            .build()
        server.enqueue(
            MockResponse.Builder()
                .code(302)
                .addHeader("Location", "http://localhost:${server.port}/admin")
                .build(),
        )

        val reader = HttpLinkReader(client = client, allowLocalhostForTesting = true)
        assertThrows(IOException::class.java) {
            runBlocking { reader.readLink("http://allowed.example:${server.port}/start") }
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun defaultReaderRejectsLocalhostWithoutReachingNetwork() = runBlocking {
        val reader = HttpLinkReader(client = client, allowLocalhostForTesting = false)
        val result = reader.readLink("http://localhost:8080/secret")

        assertNull(result.title)
        assertEquals("", result.description)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun defaultReaderRejectsBareIpWithoutReachingNetwork() = runBlocking {
        val reader = HttpLinkReader(client = client, allowLocalhostForTesting = false)
        val result = reader.readLink("http://192.168.1.1/admin")

        assertNull(result.title)
        assertEquals("", result.description)
        assertEquals(0, server.requestCount)
    }
}
