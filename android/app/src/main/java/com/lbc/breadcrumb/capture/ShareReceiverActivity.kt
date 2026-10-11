package com.lbc.breadcrumb.capture

import android.content.Intent
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.data.Memory

/** Shared text and links, and text selected anywhere (Breadcrumb in the selection toolbar). */
class ShareReceiverActivity : CaptureActivity() {

    override fun capture() {
        // CharSequence, never getStringExtra: styled text arrives as a Spanned,
        // and getStringExtra returns null for it.
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            else -> null
        }?.toString()

        if (text.isNullOrBlank()) {
            fail(getString(R.string.capture_nothing_to_save))
            return
        }

        val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
        val (sourceApp, sourceAppLabel) = SourceHelper.getSource(this)
        saveText(
            CaptureStorage.createTextMemory(
                text = text,
                subject = subject,
                sourceApp = sourceApp,
                sourceAppLabel = sourceAppLabel,
            ),
        )
    }
}
