package com.lbc.breadcrumb.open

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.capture.TextRules
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import java.io.File

/**
 * What tapping the main action on a memory's detail does (docs/design.md §12):
 * - IMAGE, PDF or AUDIO -> [ViewFile] when stored file exists, else [Missing].
 * - LINK -> [ViewUrl] (first URL; a bare "www." gets https://) else [CopyText] (rawText) else [Missing].
 * - TEXT -> [CopyText] (rawText) else [Missing].
 *
 * MIME: application/pdf for a PDF;
 * by extension jpg/jpeg -> image/jpeg, png, webp, gif, heic, heif;
 * otherwise image/any.
 */
sealed interface OriginalAction {

    data class ViewFile(val file: File, val mime: String) : OriginalAction {
        override val labelRes: Int get() = R.string.detail_action_open_original
        override val leavesApp: Boolean get() = true
        override val isEnabled: Boolean get() = true

        override fun open(context: Context) {
            val uri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.originals", file)
            } catch (_: Exception) {
                Toast.makeText(context, R.string.no_app_can_open, Toast.LENGTH_SHORT).show()
                return
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.no_app_can_open, Toast.LENGTH_SHORT).show()
            } catch (_: SecurityException) {
                Toast.makeText(context, R.string.no_app_can_open, Toast.LENGTH_SHORT).show()
            }
        }
    }

    data class ViewUrl(val url: String) : OriginalAction {
        override val labelRes: Int get() = R.string.detail_action_open_link
        override val leavesApp: Boolean get() = true
        override val isEnabled: Boolean get() = true

        override fun open(context: Context) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.no_app_can_open, Toast.LENGTH_SHORT).show()
            } catch (_: SecurityException) {
                Toast.makeText(context, R.string.no_app_can_open, Toast.LENGTH_SHORT).show()
            }
        }
    }

    data class CopyText(val text: String) : OriginalAction {
        override val labelRes: Int get() = R.string.detail_action_copy_text
        override val leavesApp: Boolean get() = false
        override val isEnabled: Boolean get() = true

        override fun open(context: Context) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("text", text))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    data object Missing : OriginalAction {
        override val labelRes: Int get() = R.string.detail_action_missing
        override val leavesApp: Boolean get() = false
        override val isEnabled: Boolean get() = false

        override fun open(context: Context) {
            // Disabled action; does nothing
        }
    }

    @get:StringRes
    val labelRes: Int

    /** True when this action opens another app, showing the "leaves the app" glyph (§11). */
    val leavesApp: Boolean

    /** False when nothing is available to hand back. */
    val isEnabled: Boolean

    /** Carries out the action on Android. */
    fun open(context: Context)

    companion object {
        /** Decides the action purely from the memory and its stored file (JVM-testable). */
        fun decide(memory: Memory, file: File?): OriginalAction {
            return when (memory.type) {
                MemoryType.IMAGE, MemoryType.PDF, MemoryType.AUDIO -> {
                    if (file != null && file.exists() && file.isFile) {
                        ViewFile(file, mimeFor(memory.type, file))
                    } else {
                        Missing
                    }
                }
                MemoryType.LINK -> {
                    val url = memory.rawText?.let(TextRules::firstUrl)
                    if (url != null) {
                        val normalized = if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url
                        ViewUrl(normalized)
                    } else {
                        val raw = memory.rawText?.trim()
                        if (!raw.isNullOrEmpty()) {
                            CopyText(raw)
                        } else {
                            Missing
                        }
                    }
                }
                MemoryType.TEXT -> {
                    val raw = memory.rawText?.trim()
                    if (!raw.isNullOrEmpty()) {
                        CopyText(raw)
                    } else {
                        Missing
                    }
                }
            }
        }

        /**
         * MIME: application/pdf for a PDF;
         * by extension jpg/jpeg -> image/jpeg, png, webp, gif, heic, heif;
         * otherwise image/any (design §12).
         */
        fun mimeFor(type: MemoryType, file: File): String {
            if (type == MemoryType.PDF || file.extension.equals("pdf", ignoreCase = true)) {
                return "application/pdf"
            }
            if (type == MemoryType.AUDIO) {
                return when (file.extension.lowercase()) {
                    "m4a" -> "audio/mp4"
                    "mp3" -> "audio/mpeg"
                    "ogg", "opus" -> "audio/ogg"
                    "aac" -> "audio/aac"
                    "amr" -> "audio/amr"
                    "3gp" -> "audio/3gpp"
                    "wav" -> "audio/wav"
                    "flac" -> "audio/flac"
                    "webm" -> "audio/webm"
                    else -> "audio/*"
                }
            }
            return when (file.extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "heic" -> "image/heic"
                "heif" -> "image/heif"
                else -> "image/*"
            }
        }
    }
}
