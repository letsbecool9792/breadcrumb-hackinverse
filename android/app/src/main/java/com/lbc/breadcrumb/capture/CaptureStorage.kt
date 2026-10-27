package com.lbc.breadcrumb.capture

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.imports.Sha256
import kotlinx.coroutines.CancellationException
import java.util.UUID

/**
 * Shared capture storage routines used by both [CaptureActivity] (external shares, tile)
 * and [Keep] (in-app "+" composer).
 */
internal object CaptureStorage {

    fun providerType(context: Context, uri: Uri): String? =
        try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }

    fun createTextMemory(
        text: String,
        subject: String? = null,
        sourceApp: String? = null,
        sourceAppLabel: String? = null,
    ): Memory {
        val (type, hasLink) = TextRules.parseText(text)
        return Memory(
            type = type,
            hasLink = hasLink,
            rawText = text,
            title = TextRules.titleFor(subject, text),
            sourceApp = sourceApp,
            sourceAppLabel = sourceAppLabel,
        )
    }

    /** Copies a shared picture or PDF in and writes its row; null when it can't be read or kept. */
    suspend fun saveFile(
        app: BreadcrumbApp,
        uri: Uri,
        declaredType: String? = null,
        rawText: String? = null,
        note: String? = null,
        hasLink: Boolean = false,
        subject: String? = null,
        sourceApp: String? = null,
        sourceAppLabel: String? = null,
    ): Memory? {
        val memory = copyFile(app, uri, declaredType, rawText, note, hasLink, subject, sourceApp, sourceAppLabel) ?: return null
        return write(app, memory)
    }

    /**
     * Copies a shared picture or PDF into the store and makes its memory, but
     * writes no row: the caller decides, as the capture sheet does when the
     * same bytes are kept already ([keptFile]). Null when it can't be read.
     */
    suspend fun copyFile(
        app: BreadcrumbApp,
        uri: Uri,
        declaredType: String? = null,
        rawText: String? = null,
        note: String? = null,
        hasLink: Boolean = false,
        subject: String? = null,
        sourceApp: String? = null,
        sourceAppLabel: String? = null,
    ): Memory? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        val mime = MediaRules.mimeOf(providerType(app, uri), declaredType) ?: return null
        val kind = MediaRules.kindOf(mime) ?: return null
        return try {
            val id = UUID.randomUUID().toString()
            val name = MediaHelper.getDisplayName(app, uri)
            val stored = app.originals.copyIn(uri, id, MediaRules.extensionFor(mime))
            Memory(
                id = id,
                type = kind,
                hasLink = hasLink,
                rawText = rawText,
                note = note,
                title = MediaRules.titleFor(subject, name, kind),
                contentCreatedAt = MediaHelper.contentDate(app, uri, stored, kind),
                sourceApp = sourceApp,
                sourceAppLabel = sourceAppLabel,
                localUri = app.originals.uriFor(stored),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Unreadable, or its grant ended when the person left: not saved.
            null
        }
    }

    /** Writes a copied file's row; null, and the copy gone, when it can't be written. */
    suspend fun write(app: BreadcrumbApp, memory: Memory): Memory? =
        try {
            app.database.memoryDao().upsert(memory)
            memory
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            app.originals.delete(memory)
            null
        }

    /**
     * The picture or PDF kept already with the same bytes as [memory]'s copy
     * (roadmap 1), or null. Only files of the same size are hashed.
     */
    suspend fun keptFile(app: BreadcrumbApp, memory: Memory): Memory? {
        val copy = app.originals.fileFor(memory) ?: return null
        val size = copy.length()
        val sameSize = app.database.captureDao().ofType(memory.type)
            .filter { it.id != memory.id }
            .mapNotNull { kept -> app.originals.fileFor(kept)?.takeIf { it.length() == size }?.let { kept to it } }
        if (sameSize.isEmpty()) return null
        val digest = Sha256.digest(copy)
        return sameSize.firstOrNull { (_, file) -> runCatching { Sha256.digest(file) }.getOrNull() == digest }?.first
    }
}
