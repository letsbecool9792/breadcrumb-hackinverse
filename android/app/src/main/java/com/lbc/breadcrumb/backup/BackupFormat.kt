package com.lbc.breadcrumb.backup

import com.lbc.breadcrumb.data.Embedding
import com.lbc.breadcrumb.data.ImportedSource
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.SpokenNote
import com.lbc.breadcrumb.data.SyncState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * A backup's `breadcrumb.json` (roadmap 4): every memory, its vector, the note
 * said over it, and what the importer has taken in. The files are beside it in
 * the zip, under `originals/`, by the names they have in the original store.
 * Pure, so the JVM tests it. Names here are the format's, not the code's: a
 * field renamed in [Memory] keeps its name here, or old backups stop reading.
 */
@Serializable
data class BackupManifest(
    val format: Int = FORMAT,
    val exportedAt: Long,
    val memories: List<BackedUpMemory>,
    val embeddings: List<BackedUpEmbedding> = emptyList(),
    val spokenNotes: List<BackedUpSpokenNote> = emptyList(),
    val importedSources: List<BackedUpSource> = emptyList(),
) {
    companion object {
        const val FORMAT = 1
    }
}

@Serializable
data class BackedUpMemory(
    val id: String,
    val type: String,
    val hasLink: Boolean = false,
    val capturedAt: Long,
    val contentCreatedAt: Long? = null,
    val sourceApp: String? = null,
    val sourceAppLabel: String? = null,
    val rawText: String? = null,
    val note: String? = null,
    /** Its original's name in the zip's `originals/`, when it had one on the phone. */
    val original: String? = null,
    val extractedText: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val entitiesJson: String? = null,
    val kind: String? = null,
    val readText: String? = null,
    val enrichedAt: Long? = null,
    val remoteId: String? = null,
    val imageSentAt: Long? = null,
    val syncState: String = SyncState.PENDING.name,
    val updatedAt: Long,
)

@Serializable
data class BackedUpEmbedding(val memoryId: String, val key: String, val vector: String, val embeddedAt: Long)

/** Its recording is `originals/<memoryId>.spoken.m4a`. */
@Serializable
data class BackedUpSpokenNote(val memoryId: String, val recordedAt: Long, val heardAt: Long? = null)

@Serializable
data class BackedUpSource(val key: String, val importedAt: Long)

object BackupFormat {

    /** The manifest's name in the zip, its first entry. */
    const val MANIFEST = "breadcrumb.json"

    /** Where the files go in the zip. */
    const val ORIGINALS = "originals/"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(manifest: BackupManifest): String = json.encodeToString(BackupManifest.serializer(), manifest)

    /** The manifest, or null when this isn't one: junk, or a newer format this app can't read. */
    fun decode(text: String): BackupManifest? =
        runCatching { json.decodeFromString(BackupManifest.serializer(), text) }.getOrNull()
            ?.takeIf { it.format <= BackupManifest.FORMAT }

    fun memory(memory: Memory, original: String?) = BackedUpMemory(
        id = memory.id,
        type = memory.type.name,
        hasLink = memory.hasLink,
        capturedAt = memory.capturedAt,
        contentCreatedAt = memory.contentCreatedAt,
        sourceApp = memory.sourceApp,
        sourceAppLabel = memory.sourceAppLabel,
        rawText = memory.rawText,
        note = memory.note,
        original = original,
        extractedText = memory.extractedText,
        title = memory.title,
        summary = memory.summary,
        entitiesJson = memory.entitiesJson,
        kind = memory.kind,
        readText = memory.readText,
        enrichedAt = memory.enrichedAt,
        remoteId = memory.remoteId,
        imageSentAt = memory.imageSentAt,
        syncState = memory.syncState.name,
        updatedAt = memory.updatedAt,
    )

    /**
     * The memory again, its original at [localUri] (null when it had none, or it
     * didn't come). Null for a kind this app doesn't know.
     */
    fun memory(backedUp: BackedUpMemory, localUri: String?): Memory? {
        val type = MemoryType.entries.firstOrNull { it.name == backedUp.type } ?: return null
        return Memory(
            id = backedUp.id,
            type = type,
            hasLink = backedUp.hasLink,
            capturedAt = backedUp.capturedAt,
            contentCreatedAt = backedUp.contentCreatedAt,
            sourceApp = backedUp.sourceApp,
            sourceAppLabel = backedUp.sourceAppLabel,
            rawText = backedUp.rawText,
            note = backedUp.note,
            localUri = localUri,
            extractedText = backedUp.extractedText,
            title = backedUp.title,
            summary = backedUp.summary,
            entitiesJson = backedUp.entitiesJson,
            kind = backedUp.kind,
            readText = backedUp.readText,
            enrichedAt = backedUp.enrichedAt,
            remoteId = backedUp.remoteId,
            imageSentAt = backedUp.imageSentAt,
            syncState = SyncState.entries.firstOrNull { it.name == backedUp.syncState } ?: SyncState.PENDING,
            updatedAt = backedUp.updatedAt,
        )
    }

    fun embedding(embedding: Embedding) = BackedUpEmbedding(
        memoryId = embedding.memoryId,
        key = embedding.key,
        vector = Base64.getEncoder().encodeToString(embedding.vector),
        embeddedAt = embedding.embeddedAt,
    )

    fun embedding(backedUp: BackedUpEmbedding): Embedding? = runCatching {
        Embedding(backedUp.memoryId, backedUp.key, Base64.getDecoder().decode(backedUp.vector), backedUp.embeddedAt)
    }.getOrNull()

    fun spokenNote(note: SpokenNote) = BackedUpSpokenNote(note.memoryId, note.recordedAt, note.heardAt)

    fun spokenNote(backedUp: BackedUpSpokenNote) = SpokenNote(backedUp.memoryId, backedUp.recordedAt, backedUp.heardAt)

    fun source(source: ImportedSource) = BackedUpSource(source.key, source.importedAt)

    fun source(backedUp: BackedUpSource) = ImportedSource(backedUp.key, backedUp.importedAt)

    /** The name a backup is offered under: "breadcrumb-2026-10-27.zip", for [day] as yyyy-MM-dd. */
    fun fileName(day: String): String = "breadcrumb-$day.zip"
}

/** What a backup or a restore is doing, for the bar over the search field and the kept sheet. */
sealed interface BackupProgress {
    data class Saving(val done: Int, val total: Int) : BackupProgress
    data class Saved(val memories: Int, val bytes: Long) : BackupProgress
    data class Restoring(val done: Int, val total: Int) : BackupProgress
    data class Restored(val added: Int, val already: Int) : BackupProgress
    data class Failed(val restoring: Boolean, val reason: String) : BackupProgress

    val finished: Boolean get() = this is Saved || this is Restored || this is Failed

    companion object {
        /** The line (design §20). */
        fun line(progress: BackupProgress?): String? = when (progress) {
            null -> null
            is Saving -> "backing up · ${progress.done} of ${progress.total}"
            is Saved -> "backed up ${progress.memories} · ${megabytes(progress.bytes)}"
            is Restoring -> "restoring · ${progress.done} of ${progress.total}"
            is Restored -> when {
                progress.added == 0 && progress.already == 0 -> "nothing in that backup"
                progress.added == 0 -> "nothing new · ${progress.already} already here"
                progress.already > 0 -> "restored ${progress.added} · ${progress.already} already here"
                else -> "restored ${progress.added}"
            }
            is Failed -> "${if (progress.restoring) "restore" else "backup"} stopped · ${progress.reason}"
        }

        /** "13 MB", "0.4 MB": megabytes of a million bytes, as the phone counts them. */
        fun megabytes(bytes: Long): String = if (bytes < 1_000_000) {
            String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
        } else {
            "${(bytes + 500_000) / 1_000_000} MB"
        }
    }
}
