package com.lbc.breadcrumb.open

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.capture.MediaReceiverActivity
import com.lbc.breadcrumb.capture.ShareReceiverActivity
import com.lbc.breadcrumb.capture.TextRules
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import java.io.File

/**
 * Sharing onward from a memory's detail (docs/design.md §12):
 * - Picture or PDF goes as the file (content URI, read grant, ClipData.newRawUri so the sheet can preview it)
 *   with the person's note as EXTRA_TEXT (its caption).
 * - Link goes as text: the note on the line before the URL, the page title as EXTRA_SUBJECT and EXTRA_TITLE
 *   (a link without a URL falls back to its words).
 * - Note goes as its words, then a blank line, then the person's note.
 * - Nothing to share -> null (no share button).
 * - Always through Intent.createChooser, with Breadcrumb's own share receivers in EXTRA_EXCLUDE_COMPONENTS,
 *   so a memory is never shared back into itself.
 */
sealed interface ShareOut {

    data class File(
        val file: java.io.File,
        val mime: String,
        val note: String? = null,
    ) : ShareOut {
        override fun share(context: Context) {
            val uri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.originals", file)
            } catch (_: Exception) {
                Toast.makeText(context, R.string.no_app_can_open, Toast.LENGTH_SHORT).show()
                return
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (!note.isNullOrBlank()) {
                    putExtra(Intent.EXTRA_TEXT, note)
                }
            }
            launchChooser(context, shareIntent)
        }
    }

    data class Text(
        val text: String,
        val subject: String? = null,
    ) : ShareOut {
        override fun share(context: Context) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                if (!subject.isNullOrBlank()) {
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TITLE, subject)
                }
            }
            launchChooser(context, shareIntent)
        }
    }

    /** Triggers the system share chooser for this memory. */
    fun share(context: Context)

    companion object {
        /** Decides the onward share purely from [memory] and stored [file] (JVM-testable). */
        fun decide(memory: Memory, file: java.io.File?): ShareOut? {
            return when (memory.type) {
                MemoryType.IMAGE, MemoryType.PDF -> {
                    if (file != null && file.exists() && file.isFile) {
                        File(
                            file = file,
                            mime = OriginalAction.mimeFor(memory.type, file),
                            note = memory.note?.trim()?.takeIf { it.isNotEmpty() },
                        )
                    } else {
                        null
                    }
                }
                MemoryType.LINK -> {
                    val url = memory.rawText?.let(TextRules::firstUrl)
                    if (url != null) {
                        val normalizedUrl = if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url
                        val note = memory.note?.trim()?.takeIf { it.isNotEmpty() }
                        val text = if (note != null) "$note\n$normalizedUrl" else normalizedUrl
                        val subject = memory.title?.trim()?.takeIf { it.isNotEmpty() }
                        Text(text = text, subject = subject)
                    } else {
                        val words = memory.rawText?.trim()?.takeIf { it.isNotEmpty() }
                        val note = memory.note?.trim()?.takeIf { it.isNotEmpty() }
                        if (words != null && note != null) {
                            Text(text = "$words\n\n$note")
                        } else if (words != null) {
                            Text(text = words)
                        } else if (note != null) {
                            Text(text = note)
                        } else {
                            null
                        }
                    }
                }
                MemoryType.TEXT -> {
                    val words = memory.rawText?.trim()?.takeIf { it.isNotEmpty() }
                    val note = memory.note?.trim()?.takeIf { it.isNotEmpty() }
                    if (words != null && note != null) {
                        Text(text = "$words\n\n$note")
                    } else if (words != null) {
                        Text(text = words)
                    } else if (note != null) {
                        Text(text = note)
                    } else {
                        null
                    }
                }
                else -> null
            }
        }

        private fun launchChooser(context: Context, shareIntent: Intent) {
            val excludeComponents = arrayOf(
                ComponentName(context, ShareReceiverActivity::class.java),
                ComponentName(context, MediaReceiverActivity::class.java),
            )

            val chooser = Intent.createChooser(shareIntent, null).apply {
                putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excludeComponents)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            try {
                context.startActivity(chooser)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.no_app_can_open, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
