package com.lbc.breadcrumb.net

import com.lbc.breadcrumb.data.Memory
import kotlinx.serialization.Serializable

/**
 * What the upload queue sends and gets back: docs/api.md, and
 * server/src/api.ts, hand-written to match. Change all three together.
 */
interface UploadApi {
    /** POST /memories, ten at a time. */
    suspend fun sendMemories(memories: List<UploadMemory>): Reply<List<IngestResult>>

    /** POST /memories/images, four at a time. */
    suspend fun sendPictures(pictures: List<UploadPicture>): Reply<List<IngestResult>>

    /** POST /memories/enrichment, fifty at a time. */
    suspend fun askEnrichment(ids: List<String>): Reply<List<EnrichmentReply>>

    /** POST /memories/delete: how many the server held. */
    suspend fun sendDeletes(ids: List<String>): Reply<Int>
}

/** How a request went, in the three ways the phone acts on (docs/api.md, "Status codes"). */
sealed interface Reply<out T> {
    /** 200. */
    data class Done<T>(val body: T) : Reply<T>

    /**
     * Try again later, with backoff: a 503 (whose [body] still says which
     * memories were finished, when it says), any other answer the phone does
     * not act on, or no answer at all.
     */
    data class Later<T>(val body: T?, val why: String) : Reply<T>

    /**
     * 400 or 413: the request is wrong, and the same bytes would be refused
     * again. [invalid] holds the positions the server named; empty means all.
     */
    data class Refused(val code: Int, val invalid: List<Int>) : Reply<Nothing>
}

/**
 * One memory as POST /memories takes it: the Room row, minus everything that
 * is the phone's own. **Never `localUri`**, where the original lives on the
 * phone (architecture rule 1), and never what the server itself made
 * (summary, kind, readText). No defaults, so every field is written, nulls
 * included.
 */
@Serializable
data class UploadMemory(
    val id: String,
    val type: String,
    val hasLink: Boolean,
    val capturedAt: Long,
    val contentCreatedAt: Long?,
    val sourceApp: String?,
    val sourceAppLabel: String?,
    val title: String?,
    val rawText: String?,
    val extractedText: String?,
    val note: String?,
    val updatedAt: Long,
)

fun Memory.toUpload() = UploadMemory(
    id = id,
    type = type.name,
    hasLink = hasLink,
    capturedAt = capturedAt,
    contentCreatedAt = contentCreatedAt,
    sourceApp = sourceApp,
    sourceAppLabel = sourceAppLabel,
    title = title,
    rawText = rawText,
    extractedText = extractedText,
    note = note,
    updatedAt = updatedAt,
)

/** One picture for POST /memories/images: the bytes themselves, in base64, never a path. */
@Serializable
data class UploadPicture(val id: String, val mimeType: String, val data: String)

/** One memory's outcome, in the order sent. */
@Serializable
data class IngestResult(
    val id: String,
    val enriched: Boolean = false,
    val embedded: Boolean = false,
    val reason: String? = null,
    /** Stored, but a model call failed for a reason that may pass: the phone sends it again. */
    val retryable: Boolean = false,
)

/** What the model made of one memory. Nulls when the server holds it but has not read it. */
@Serializable
data class EnrichmentReply(
    val id: String,
    val summary: String? = null,
    val kind: String? = null,
    val readText: String? = null,
)

@Serializable
internal data class Results<T>(val results: List<T>)

@Serializable
internal data class Ids(val ids: List<String>)

@Serializable
internal data class Deleted(val deleted: Int)

@Serializable
internal data class Refusal(val invalid: List<Invalid> = emptyList()) {
    @Serializable
    data class Invalid(val index: Int)
}
