package com.lbc.breadcrumb.capture

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import kotlinx.coroutines.launch
import java.util.UUID

class MediaReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = mutableListOf<Uri>()
        if (intent.action == Intent.ACTION_SEND) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
        } else if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
        }

        if (uris.isEmpty()) {
            Toast.makeText(this, R.string.capture_nothing_to_save, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val caption = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val hasLink = caption != null && TextRules.parseText(caption).second
        val (sourceApp, sourceAppLabel) = SourceHelper.getSource(this)

        val app = application as BreadcrumbApp

        lifecycleScope.launch {
            for (uri in uris) {
                val mimeType = contentResolver.getType(uri) ?: ""
                val isPdf = mimeType == "application/pdf" || mimeType.endsWith("pdf")
                val extension = if (isPdf) "pdf" else mimeType.substringAfterLast('/', "jpg")

                val id = UUID.randomUUID().toString()
                try {
                    val (file, title, createdAt) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val copiedFile = app.originals.copyIn(uri, id, extension)
                        val extractedTitle = if (isPdf) MediaHelper.getDisplayName(this@MediaReceiverActivity, uri) else null
                        val extractedDate = if (!isPdf) MediaHelper.getDateTaken(this@MediaReceiverActivity, uri) else null
                        Triple(copiedFile, extractedTitle, extractedDate)
                    }

                    val memory = Memory(
                        id = id,
                        type = if (isPdf) MemoryType.PDF else MemoryType.IMAGE,
                        hasLink = hasLink,
                        rawText = caption,
                        title = title,
                        contentCreatedAt = createdAt,
                        sourceApp = sourceApp,
                        sourceAppLabel = sourceAppLabel,
                        localUri = app.originals.uriFor(file)
                    )

                    // Run the DB insert in applicationScope so it survives if this activity is killed
                    app.applicationScope.launch {
                        app.database.memoryDao().upsert(memory)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            Toast.makeText(this@MediaReceiverActivity, R.string.capture_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
