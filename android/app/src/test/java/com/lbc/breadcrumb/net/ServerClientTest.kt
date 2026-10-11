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
import java.io.EOFException
import java.io.IOException
import java.net.NoRouteToHostException
import java.net.SocketException
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
    fun `a connection closed under the request is the same on every platform, whatever its words`() {
        for (words in listOf("Connection reset", "Broken pipe", "An established connection was aborted by the software in your host machine")) {
            assertEquals(words, ServerStatus.ClosedWithoutReply, statusOf(SocketException(words)))
        }
        assertEquals(ServerStatus.ClosedWithoutReply, statusOf(IOException("unexpected end of stream on http://localhost:3000/...", EOFException())))
    }

    @Test
    fun `no route to the host is unreachable, not closed`() {
        assertEquals(ServerStatus.Unreachable("No route to host"), statusOf(NoRouteToHostException("No route to host")))
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

    @Test
    fun `search returns found with hits and interpretation`() = runBlocking {
        val jsonBody = """
            {
              "query": "internship",
              "interpretation": {
                "query": "internship",
                "types": ["IMAGE"],
                "from": "2026-04-01",
                "to": "2026-04-30",
                "sourceApp": null
              },
              "results": [
                {
                  "id": "mem-1",
                  "score": 0.0325,
                  "ranks": { "vector": 1, "text": 2 },
                  "type": "IMAGE",
                  "hasLink": false,
                  "summary": "Qualcomm SWE internship"
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(jsonBody).build())

        val outcome = client().search("internship screenshot from april", limit = 20)
        assertTrue(outcome is SearchOutcome.Found)
        val found = outcome as SearchOutcome.Found
        assertEquals("internship", found.interpretation?.query)
        assertEquals(listOf("IMAGE"), found.interpretation?.types)
        assertEquals(1, found.hits.size)
        assertEquals("mem-1", found.hits[0].id)
        assertEquals(1, found.hits[0].ranks?.vector)
        assertEquals(2, found.hits[0].ranks?.text)

        val recorded = server.takeRequest()
        assertEquals("/search", recorded.url.encodedPath)
        assertEquals("internship screenshot from april", recorded.url.queryParameter("q"))
        assertEquals("20", recorded.url.queryParameter("limit"))
    }

    @Test
    fun `search handles null interpretation`() = runBlocking {
        val jsonBody = """
            {
              "query": "qualcomm",
              "interpretation": null,
              "interpretationError": "model parse error",
              "results": [
                {
                  "id": "mem-2"
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(jsonBody).build())

        val outcome = client().search("qualcomm")
        assertTrue(outcome is SearchOutcome.Found)
        val found = outcome as SearchOutcome.Found
        assertEquals(null, found.interpretation)
        assertEquals(1, found.hits.size)
        assertEquals("mem-2", found.hits[0].id)
    }

    @Test
    fun `a non-2xx is unavailable, not offline`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(503).body("""{"error":"could not read the search"}""").build())

        val outcome = client().search("query")
        assertTrue(outcome is SearchOutcome.Unavailable)
        val unavail = outcome as SearchOutcome.Unavailable
        assertEquals("HTTP 503", unavail.reason)
        assertEquals(false, unavail.offline)
    }

    @Test
    fun `a malformed 200 body is unavailable, not offline`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body("not valid json").build())

        val outcome = client().search("query")
        assertTrue(outcome is SearchOutcome.Unavailable)
        val unavail = outcome as SearchOutcome.Unavailable
        assertEquals(false, unavail.offline)
    }

    @Test
    fun `a refused connection is offline`() = runBlocking {
        val port = server.port
        server.close()

        val outcome = ServerClient("http://127.0.0.1:$port/").search("query")
        assertTrue(outcome is SearchOutcome.Unavailable)
        val unavail = outcome as SearchOutcome.Unavailable
        assertEquals(true, unavail.offline)
    }
}
