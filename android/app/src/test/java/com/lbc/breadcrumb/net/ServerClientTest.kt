package com.lbc.breadcrumb.net

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class ServerClientTest {

    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun client(timeoutMillis: Long = 2_000) = ServerClient(server.url("/").toString(), healthTimeoutMillis = timeoutMillis)

    @Test
    fun `breadcrumb's server is reachable`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("""{"service":"breadcrumb","status":"ok"}""").build())

        assertEquals(ServerStatus.Reachable, client().checkHealth())
        assertEquals("/health", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `another server on the port is told apart`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("""{"status":"ok"}""").build())
        server.enqueue(MockResponse.Builder().code(404).body("<html>Cannot GET /health</html>").build())

        assertTrue(client().checkHealth() is ServerStatus.OtherServer)
        assertEquals(ServerStatus.OtherServer("HTTP 404"), client().checkHealth())
    }

    @Test
    fun `nothing listening is refused, as with no adb reverse`() = runBlocking {
        val port = server.port
        server.close()

        assertEquals(ServerStatus.Refused, ServerClient("http://127.0.0.1:$port/").checkHealth())
    }

    @Test
    fun `accepted then closed without a reply is the forward with no server behind it`() = runBlocking {
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())

        assertEquals(ServerStatus.ClosedWithoutReply, client().checkHealth())
    }

    @Test
    fun `a server that never answers times out`() = runBlocking {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build())

        assertEquals(ServerStatus.TimedOut, client(timeoutMillis = 500).checkHealth())
    }

    @Test
    fun `anything else is unreachable, with its reason`() {
        assertEquals(ServerStatus.TimedOut, statusOf(SocketTimeoutException("read timed out")))
        assertEquals(ServerStatus.Unreachable("CLEARTEXT communication not permitted"), statusOf(IOException("CLEARTEXT communication not permitted")))
    }

    @Test
    fun `uploads wait at least 90 s, since the server holds a paced request up to a minute`() {
        val uploads = client().uploadClient
        assertTrue(uploads.readTimeoutMillis >= 90_000)
        assertTrue(uploads.callTimeoutMillis >= 90_000)
    }
}
