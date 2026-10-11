package com.lbc.breadcrumb.net

import com.lbc.breadcrumb.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The phone's half of docs/api.md: hand-written to match it and
 * server/src/api.ts, which change together. Two timeouts over one client: a
 * few seconds to tell whether the server is there, and at least 90 s for
 * uploads, since the server holds a paced request up to a minute while it
 * waits for a free model slot, and a shorter wait turns each one into a
 * timeout and a backoff.
 */
class ServerClient(
    baseUrl: String = BuildConfig.SERVER_URL,
    base: OkHttpClient = OkHttpClient(),
    healthTimeoutMillis: Long = 5_000,
) : UploadApi {
    internal val root: HttpUrl = baseUrl.toHttpUrl()

    private val healthClient = base.newBuilder()
        .connectTimeout(healthTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(healthTimeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(healthTimeoutMillis, TimeUnit.MILLISECONDS)
        // A check says what happened, rather than quietly trying again.
        .retryOnConnectionFailure(false)
        .build()

    internal val uploadClient = base.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(UPLOAD_TIMEOUT_SECONDS + 30, TimeUnit.SECONDS)
        .build()

    internal val searchClient = base.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * GET /search?q=...&limit=...
     * 20 s call timeout. Returns Found or Unavailable.
     */
    suspend fun search(query: String, limit: Int = 30): SearchOutcome {
        val url = root.newBuilder()
            .addPathSegment("search")
            .addQueryParameter("q", query)
            .addQueryParameter("limit", limit.toString())
            .build()
        val request = Request.Builder().url(url).get().build()
        return try {
            searchClient.newCall(request).answer { response ->
                val body = response.body.string()
                if (response.isSuccessful) {
                    val reply = runCatching { json.decodeFromString<SearchReply>(body) }.getOrNull()
                    if (reply != null) {
                        SearchOutcome.Found(reply.results, reply.interpretation)
                    } else {
                        SearchOutcome.Unavailable("Malformed search response", offline = false)
                    }
                } else {
                    SearchOutcome.Unavailable("HTTP ${response.code}", offline = false)
                }
            }
        } catch (error: IOException) {
            SearchOutcome.Unavailable(statusOf(error).toString(), offline = true)
        }
    }

    /** Whether Breadcrumb's server is there, and if not, which of the ways it is not. */
    suspend fun checkHealth(): ServerStatus {
        val request = Request.Builder().url(root.resolve("health")!!).get().build()
        return try {
            healthClient.newCall(request).answer { response ->
                val reply = runCatching { json.decodeFromString<HealthReply>(response.body.string()) }.getOrNull()
                when {
                    // /health names the service, so another server holding the port is not taken for this one.
                    response.isSuccessful && reply?.service == SERVICE -> ServerStatus.Reachable
                    else -> ServerStatus.OtherServer("HTTP ${response.code}")
                }
            }
        } catch (error: IOException) {
            statusOf(error)
        }
    }

    override suspend fun sendMemories(memories: List<UploadMemory>) =
        post("memories", json.encodeToString(memories)) { json.decodeFromString<Results<IngestResult>>(it).results }

    override suspend fun sendPictures(pictures: List<UploadPicture>) =
        post("memories/images", json.encodeToString(pictures)) { json.decodeFromString<Results<IngestResult>>(it).results }

    override suspend fun askEnrichment(ids: List<String>) =
        post("memories/enrichment", json.encodeToString(Ids(ids))) { json.decodeFromString<Results<EnrichmentReply>>(it).results }

    override suspend fun sendDeletes(ids: List<String>) =
        post("memories/delete", json.encodeToString(Ids(ids))) { json.decodeFromString<Deleted>(it).deleted }

    /** POST /parse with {"phrase": string, "sources": string[]} */
    suspend fun parse(phrase: String, sources: List<String>): Reply<Interpretation> =
        post("parse", json.encodeToString(ParseRequest(phrase, sources))) { json.decodeFromString<ParseReply>(it).interpretation }

    /** POST /memories/forget: deletes every memory the server holds */
    suspend fun forgetAll(): Reply<Int> =
        post("memories/forget", "{}") { json.decodeFromString<Deleted>(it).deleted }

    /**
     * Posts [body] and sorts the answer into the three ways the phone acts on.
     * A 503's body is read too: it still says which memories were finished.
     */
    private suspend fun <T> post(path: String, body: String, read: (String) -> T): Reply<T> {
        val request = Request.Builder().url(root.resolve(path)!!).post(body.toRequestBody(JSON_TYPE)).build()
        return try {
            uploadClient.newCall(request).answer { response ->
                val text = response.body.string()
                val answer = runCatching { read(text) }.getOrNull()
                when {
                    response.code == 200 && answer != null -> Reply.Done(answer)
                    response.code == 400 || response.code == 413 -> Reply.Refused(
                        response.code,
                        runCatching { json.decodeFromString<Refusal>(text).invalid.map { it.index } }.getOrDefault(emptyList()),
                    )
                    else -> Reply.Later(answer, "HTTP ${response.code}")
                }
            }
        } catch (error: IOException) {
            Reply.Later(null, statusOf(error).toString())
        }
    }

    internal companion object {
        const val SERVICE = "breadcrumb"
        const val UPLOAD_TIMEOUT_SECONDS = 100L
        const val SEARCH_TIMEOUT_SECONDS = 20L
        val JSON_TYPE = "application/json".toMediaType()
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** What a health check found. */
sealed interface ServerStatus {
    data object Reachable : ServerStatus

    /** Nothing listening on the port: on the phone, no `adb reverse tcp:3000 tcp:3000`. */
    data object Refused : ServerStatus

    /** Connected, then closed without a reply: the forward is set, but no server is running behind it. */
    data object ClosedWithoutReply : ServerStatus

    /** Something answered, but not Breadcrumb's server. */
    data class OtherServer(val detail: String) : ServerStatus

    data object TimedOut : ServerStatus

    data class Unreachable(val reason: String) : ServerStatus
}

@Serializable
internal data class HealthReply(val service: String? = null, val status: String? = null)

/**
 * Tells the ways a call can fail apart. Pure, so JVM tests cover it.
 *
 * By type, never by message: the words differ by platform. A peer closing
 * under the request reads "Connection reset" or "Broken pipe" on the phone and
 * "An established connection was aborted" on Windows, and is the same thing.
 */
internal fun statusOf(error: IOException): ServerStatus = when {
    error is InterruptedIOException -> ServerStatus.TimedOut
    error is ConnectException -> ServerStatus.Refused
    // Socket errors before a connection: there was nowhere to send it.
    error is NoRouteToHostException || error is PortUnreachableException -> ServerStatus.Unreachable(error.message ?: error.javaClass.simpleName)
    // Any other socket error came after the connection was made: it closed under the request.
    error is SocketException || error is EOFException || error.cause is EOFException ||
        error.message?.contains("unexpected end of stream") == true -> ServerStatus.ClosedWithoutReply
    else -> ServerStatus.Unreachable(error.message ?: error.javaClass.simpleName)
}

/**
 * Sends the call and reads its answer with [read], all on the IO dispatcher.
 *
 * Reading a body is network work, and some callers (the search, in the view
 * model; the debug list's server line) run on the main thread, where Android
 * refuses it: a quick answer arrives already buffered, and a slow one, like a
 * vector search, crashed the app with NetworkOnMainThreadException. The answer
 * is closed here too, since closing one not read to its end reads the rest.
 */
private suspend fun <T> Call.answer(read: (Response) -> T): T =
    withContext(Dispatchers.IO) { await().use(read) }

/** OkHttp's call as a suspend function, cancelled with its coroutine. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        },
    )
}
