package com.lbc.breadcrumb.capture

import android.content.ClipboardManager
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.capture.ClipboardRules.Decision
import com.lbc.breadcrumb.data.Memory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the Quick Settings tile saves: whatever is on the clipboard. */
class ClipboardCaptureActivity : CaptureActivity() {

    private var read = false
    private var timeout: Job? = null

    override fun capture() {
        // Android 10+ hands the clipboard only to the window with focus, which
        // arrives after onCreate. A window that never gains focus must not sit
        // there, and must not close without a word either.
        timeout = lifecycleScope.launch {
            delay(FOCUS_TIMEOUT_MS)
            if (!read) {
                read = true
                fail(getString(R.string.capture_clipboard_unreadable))
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || read) return
        read = true
        timeout?.cancel()
        try {
            saveClipboard()
        } catch (_: Exception) {
            fail(getString(R.string.capture_failed))
        }
    }

    private fun saveClipboard() {
        val clip = getSystemService(ClipboardManager::class.java).primaryClip
        if (clip == null || clip.itemCount == 0) {
            fail(getString(R.string.capture_clipboard_empty))
            return
        }

        // Apps set it whatever the API level, though the constant arrived in 33.
        val sensitive = clip.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE) == true
        val texts = mutableListOf<String>()
        val files = mutableListOf<Uri>()
        for (i in 0 until clip.itemCount) {
            val item = clip.getItemAt(i)
            val uri = item.uri
            if (uri != null && uri.scheme == ContentResolver.SCHEME_CONTENT && !isText(uri)) {
                files += uri
            } else {
                // Text, HTML, an intent, or a text document: all as words.
                item.coerceToText(this)?.toString()?.let { texts += it }
            }
        }

        when (val decision = ClipboardRules.decide(sensitive, texts, files.size)) {
            Decision.Empty -> fail(getString(R.string.capture_clipboard_empty))
            Decision.Sensitive -> fail(getString(R.string.capture_clipboard_sensitive))
            is Decision.Text -> {
                val (type, hasLink) = TextRules.parseText(decision.text)
                saveText(Memory(type = type, hasLink = hasLink, rawText = decision.text))
            }
            Decision.Files -> saveFiles(
                uris = files,
                declaredType = clip.description?.takeIf { it.mimeTypeCount > 0 }?.getMimeType(0),
                text = null,
                subject = null,
                source = null to null,
            )
        }
    }

    private fun isText(uri: Uri): Boolean =
        try {
            contentResolver.getType(uri)?.startsWith("text/") == true
        } catch (_: Exception) {
            false
        }

    private companion object {
        const val FOCUS_TIMEOUT_MS = 2_000L
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}
