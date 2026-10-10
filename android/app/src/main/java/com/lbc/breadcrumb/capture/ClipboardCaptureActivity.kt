package com.lbc.breadcrumb.capture

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ClipboardCaptureActivity : ComponentActivity() {

    private var timeoutJob: Job? = null
    private var hasReadClipboard = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Give it a 2s timeout so a window that never gains focus cannot sit invisibly
        timeoutJob = lifecycleScope.launch {
            delay(2000)
            if (!hasReadClipboard) {
                closeCleanly()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !hasReadClipboard) {
            hasReadClipboard = true
            timeoutJob?.cancel()
            readClipboardAndSave()
        }
    }

    private fun readClipboardAndSave() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip

        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, R.string.capture_nothing_to_save, Toast.LENGTH_SHORT).show()
            closeCleanly()
            return
        }

        val isSensitive = clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") ?: false
        if (isSensitive) {
            Toast.makeText(this, R.string.capture_nothing_to_save, Toast.LENGTH_SHORT).show()
            closeCleanly()
            return
        }

        val item = clip.getItemAt(0)
        val uri = item.uri
        val text = item.text?.toString()

        val app = application as BreadcrumbApp

        if (uri != null) {
            lifecycleScope.launch {
                val mimeType = contentResolver.getType(uri) ?: ""
                val isPdf = mimeType == "application/pdf" || mimeType.endsWith("pdf")
                val extension = if (isPdf) "pdf" else mimeType.substringAfterLast('/', "jpg")
                val id = UUID.randomUUID().toString()
                
                try {
                    val (file, title, createdAt) = withContext(Dispatchers.IO) {
                        val copiedFile = app.originals.copyIn(uri, id, extension)
                        val extractedTitle = if (isPdf) MediaHelper.getDisplayName(this@ClipboardCaptureActivity, uri) else null
                        val extractedDate = if (!isPdf) MediaHelper.getDateTaken(this@ClipboardCaptureActivity, uri) else null
                        Triple(copiedFile, extractedTitle, extractedDate)
                    }

                    val memory = Memory(
                        id = id,
                        type = if (isPdf) MemoryType.PDF else MemoryType.IMAGE,
                        title = title,
                        contentCreatedAt = createdAt,
                        localUri = app.originals.uriFor(file)
                    )

                    app.applicationScope.launch {
                        app.database.memoryDao().upsert(memory)
                    }
                    Toast.makeText(this@ClipboardCaptureActivity, R.string.capture_saved, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@ClipboardCaptureActivity, R.string.capture_nothing_to_save, Toast.LENGTH_SHORT).show()
                }
                closeCleanly()
            }
        } else if (!text.isNullOrBlank()) {
            val (type, hasLink) = TextRules.parseText(text)
            val memory = Memory(
                type = type,
                hasLink = hasLink,
                rawText = text
            )

            app.applicationScope.launch {
                app.database.memoryDao().upsert(memory)
            }
            Toast.makeText(this, R.string.capture_saved, Toast.LENGTH_SHORT).show()
            closeCleanly()
        } else {
            Toast.makeText(this, R.string.capture_nothing_to_save, Toast.LENGTH_SHORT).show()
            closeCleanly()
        }
    }

    private fun closeCleanly() {
        val decorView = window.decorView
        decorView.visibility = View.INVISIBLE
        decorView.postOnAnimation {
            decorView.postOnAnimation {
                finish()
            }
        }
    }
}
