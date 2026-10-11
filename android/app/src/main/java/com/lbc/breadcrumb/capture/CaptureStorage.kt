package com.lbc.breadcrumb.capture

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.data.Memory
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
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        val mime = MediaRules.mimeOf(providerType(app, uri), declaredType) ?: return null
        val kind = MediaRules.kindOf(mime) ?: return null
        return try {
            val id = UUID.randomUUID().toString()
            val name = MediaHelper.getDisplayName(app, uri)
            val stored = app.originals.copyIn(uri, id, MediaRules.extensionFor(mime))
            val memory = Memory(
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
            app.database.memoryDao().upsert(memory)
            memory
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Unreadable, or its grant ended when the person left: not saved.
            null
        }
    }
}
