package com.lbc.breadcrumb.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One saved thing. The row is written the instant the user shares or taps the
 * tile; everything downstream (OCR, the server's reading, upload) fills in
 * nullable columns later. Saving must never wait on any of it.
 *
 * Every column every workstream needs is here from the start, so no two
 * workstreams change the schema at once. A change after this is a schema
 * version bump with an AutoMigration (see BreadcrumbDatabase).
 */
@Entity(
    tableName = "memories",
    indices = [
        Index("capturedAt"),   // list ordering
        Index("syncState"),    // upload queue lookup
        Index("type"),         // type filter during retrieval
    ],
)
data class Memory(
    /**
     * Generated on-device so an offline save has a stable identity before the
     * server has ever seen it. Re-sending is then idempotent.
     */
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    /**
     * The primary kind -- what the memory *is*, and so what its tile looks
     * like. A file decides it (IMAGE, PDF); otherwise LINK when the text
     * carries a URL; otherwise TEXT.
     */
    val type: MemoryType,

    /**
     * True when the shared text carries a URL, whatever the primary type. This
     * is what lets a photo sent with a link wear both an IMAGE and a LINK chip,
     * and what a "that link" search must filter on -- `type = LINK` alone
     * misses every captioned photo and PDF.
     *
     * Set from shared text only, never from OCR: a screenshot with a URL bar in
     * it is not a link someone sent.
     */
    @ColumnInfo(defaultValue = "0")
    val hasLink: Boolean = false,

    /** When the user saved it. */
    val capturedAt: Long = System.currentTimeMillis(),

    /**
     * When the underlying content was created, when that is knowable -- a
     * photo's date taken, a screenshot's own file date. This is what answers
     * "from April"; [capturedAt] often is not the same thing.
     */
    val contentCreatedAt: Long? = null,

    /**
     * Package name of the app it came from, via Activity.getReferrer(). Null
     * when unknown, or when the referrer was a system surface (the clipboard,
     * the share chooser) rather than a real source.
     */
    val sourceApp: String? = null,

    /**
     * The source app's name as a person would say it -- "WhatsApp", not
     * "com.whatsapp". Resolved and stored at capture, so it survives the app
     * being uninstalled. This is what a "from WhatsApp" search matches against;
     * it is a filter, and deliberately not part of [searchableText].
     */
    val sourceAppLabel: String? = null,

    /** Shared text, URL, or clipboard content. */
    val rawText: String? = null,

    /**
     * The person's own words about it, written when saving or later. Never
     * required: saving stays one tap. Kept apart from [rawText], which is what
     * was shared, so the two can be told apart and the note edited.
     */
    val note: String? = null,

    /** App-private copy of the original file, for images, PDFs and voice notes ([OriginalStore]). */
    val localUri: String? = null,

    /**
     * What the phone read out of the thing: OCR of a picture, a PDF's text, a
     * link's page description, what was said in a voice note. **Null means not read yet; empty means read,
     * and holding no text.** The reading queues find their work by that null,
     * so never write empty to mean anything else.
     */
    val extractedText: String? = null,

    /**
     * Starts as whatever the sharing app offered as a subject (Chrome sends the
     * page title), so the list reads properly before anything has been
     * processed. A link's page title fills it when nothing else did.
     */
    val title: String? = null,

    /**
     * The model's one line about it, copied back from the server, so a memory
     * opened from the home screen says what it is without a search, and local
     * word search finds it by that line offline.
     */
    val summary: String? = null,
    val entitiesJson: String? = null,

    /** What kind of thing the model took it for -- "job posting", "screenshot". Copied back with [summary]. */
    val kind: String? = null,

    /** What the model saw in a picture OCR could barely read. Copied back with [summary]. */
    val readText: String? = null,

    /**
     * When the phone last copied [summary], [kind] and [readText] from the
     * server. Null means "ask": every send makes the copy stale, since the
     * server may have read the memory again.
     */
    val enrichedAt: Long? = null,

    /** Server-side id, once this row has synced. */
    val remoteId: String? = null,

    /**
     * When the picture itself was sent for reading. Only images whose words
     * OCR could not read are sent, and only once -- a picture costs about a
     * thousand model tokens, against a few hundred for text.
     */
    val imageSentAt: Long? = null,

    val syncState: SyncState = SyncState.PENDING,

    val updatedAt: Long = System.currentTimeMillis(),
) {
    /**
     * Everything worth matching against, newline-joined: what local word
     * search indexes ([MemoryFts] holds the same fields).
     */
    val searchableText: String
        get() = listOfNotNull(title, note, summary, rawText, extractedText)
            .filter { it.isNotBlank() }
            .joinToString("\n")

    /** Primary type first, then LINK when a link rides along with something else. */
    val chips: List<MemoryType>
        get() = if (hasLink && type != MemoryType.LINK) listOf(type, MemoryType.LINK) else listOf(type)
}

enum class MemoryType {
    TEXT,
    LINK,
    IMAGE,
    PDF,

    /** A voice note, recorded in the app or shared in. Declared in V1, so it came without a migration. */
    AUDIO,
}

enum class SyncState {
    /** Saved locally, not yet sent. */
    PENDING,

    /** Handed to WorkManager, upload in flight. */
    UPLOADING,

    /** Stored server-side. */
    SYNCED,

    /** The server refused it. Not retried automatically: the same bytes would be refused again. */
    FAILED,
}
