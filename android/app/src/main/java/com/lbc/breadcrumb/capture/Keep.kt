package com.lbc.breadcrumb.capture

import android.content.Context
import android.net.Uri
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.ocr.workScheduler
import kotlinx.coroutines.async

const val ACTION_KEEP = "com.lbc.breadcrumb.action.KEEP"

/**
 * What Keep does from inside the app (the "+" sheet), on the application's scope:
 * - Text alone is one memory by the share rules (a note, or a link when it holds a URL),
 *   with no source app.
 * - With files, each file is its own memory, copied in as a share would be, with the text as
 *   the note on every one.
 * - It schedules an upload and says how many it kept; none (0) is a failure.
 * - It shares the code paths [CaptureActivity] already uses ([CaptureStorage]).
 */
object Keep {

    suspend fun keep(context: Context, draft: Draft): Int =
        keep(context, draft.text, draft.attachments.map { it.contentUri })

    suspend fun keep(
        context: Context,
        text: String? = null,
        uris: List<Uri> = emptyList(),
    ): Int {
        val app = context.applicationContext as BreadcrumbApp
        return app.applicationScope.async {
            val trimmedText = text?.trim()?.takeIf { it.isNotEmpty() }
            if (uris.isEmpty()) {
                if (trimmedText == null) return@async 0
                val memory = CaptureStorage.createTextMemory(trimmedText)
                app.database.memoryDao().upsert(memory)
                workScheduler(app)
                return@async 1
            }

            val hasLink = trimmedText?.let { TextRules.parseText(it).second } ?: false
            var keptCount = 0
            for (uri in uris) {
                val memory = CaptureStorage.saveFile(
                    app = app,
                    uri = uri,
                    note = trimmedText,
                    hasLink = hasLink,
                )
                if (memory != null) {
                    keptCount++
                }
            }
            if (keptCount > 0) {
                workScheduler(app)
            }
            keptCount
        }.await()
    }
}
