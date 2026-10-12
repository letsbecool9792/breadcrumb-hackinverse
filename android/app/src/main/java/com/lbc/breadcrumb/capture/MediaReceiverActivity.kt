package com.lbc.breadcrumb.capture

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import com.lbc.breadcrumb.R

/** Pictures and PDFs, one or many, from the share sheet. */
class MediaReceiverActivity : CaptureActivity() {

    override fun capture() {
        val streams = when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        val clip = intent.clipData?.let { data -> (0 until data.itemCount).mapNotNull { data.getItemAt(it).uri } }.orEmpty()
        val uris = MediaRules.sharedFiles(streams, clip)
        if (uris.isEmpty()) {
            fail(getString(R.string.capture_nothing_to_save))
            return
        }

        saveFiles(
            uris = uris,
            declaredType = intent.type,
            text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
            source = SourceHelper.getSource(this),
        )
    }
}
