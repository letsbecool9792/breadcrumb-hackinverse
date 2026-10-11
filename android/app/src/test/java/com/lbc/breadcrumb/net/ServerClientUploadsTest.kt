package com.lbc.breadcrumb.net

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServerClientUploadsTest {

    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private val client by lazy { ServerClient(server.url("/").toString()) }

    private fun memory(id: String) = UploadMemory(id, "TEXT", false, 1_000, null, null, null, null, "words", null, null, 1_000)

    private fun answer(code: Int, body: String) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun `memories go as a JSON array to POST memories, and a 200 is done`() = runBlocking {
        answer(200, """{"results":[{"id":"a","enriched":true,"embedded":true}]}""")

        val reply = client.sendMemories(listOf(memory("a")))

        assertEquals(Reply.Done(listOf(IngestResult("a", enriched = true, embedded = true))), reply)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/memories", request.url.encodedPath)
        assertTrue(request.headers["Content-Type"]!!.startsWith("application/json"))
        assertTrue(request.body!!.utf8().startsWith("""[{"id":"a","type":"TEXT""""))
    }

    @Test
    fun `a 503 is later, and still says which memories were finished`() = runBlocking {
        answer(503, """{"results":[{"id":"a","enriched":true,"embedded":true},{"id":"b","enriched":false,"embedded":true,"reason":"rate limited","retryable":true}]}""")

        val reply = client.sendMemories(listOf(memory("a"), memory("b")))

        assertTrue(reply is Reply.Later)
        assertEquals(listOf(false, true), (reply as Reply.Later).body!!.map { it.retryable })
    }

    @Test
    fun `a 503 with no results, from the database, is later with nothing finished`() = runBlocking {
        answer(503, """{"error":"the database did not answer","retryable":true}""")

        assertEquals(Reply.Later(null, "HTTP 503"), client.askEnrichment(listOf("a")))
    }

    @Test
    fun `a 400 is refused, naming the memories the server found wrong`() = runBlocking {
        answer(400, """{"error":"invalid memory","invalid":[{"index":1,"errors":["type: one of TEXT, LINK, IMAGE, PDF, AUDIO"]}]}""")

        assertEquals(Reply.Refused(400, listOf(1)), client.sendMemories(listOf(memory("a"), memory("b"))))
    }

    @Test
    fun `a body too large is refused as a whole`() = runBlocking {
        answer(413, """{"error":"the body is too large"}""")

        assertEquals(Reply.Refused(413, emptyList()), client.sendPictures(listOf(UploadPicture("a", "image/jpeg", "AAAA"))))
    }

    @Test
    fun `a route not built yet is later, not refused`() = runBlocking {
        answer(501, """{"error":"not built yet"}""")

        assertEquals(Reply.Later(null, "HTTP 501"), client.sendDeletes(listOf("a")))
    }

    @Test
    fun `no server is later`() = runBlocking {
        val port = server.port
        server.close()

        assertEquals(Reply.Later(null, "Refused"), ServerClient("http://127.0.0.1:$port/").sendDeletes(listOf("a")))
    }

    @Test
    fun `enrichment and deletes read their answers`() = runBlocking {
        answer(200, """{"results":[{"id":"a","summary":"A job posting","kind":"job posting","readText":null}]}""")
        answer(200, """{"deleted":2}""")

        assertEquals(Reply.Done(listOf(EnrichmentReply("a", "A job posting", "job posting", null))), client.askEnrichment(listOf("a", "b")))
        assertEquals("""{"ids":["a","b"]}""", server.takeRequest().body!!.utf8())
        assertEquals(Reply.Done(2), client.sendDeletes(listOf("a", "b")))
        assertEquals("/memories/delete", server.takeRequest().url.encodedPath)
    }
}
