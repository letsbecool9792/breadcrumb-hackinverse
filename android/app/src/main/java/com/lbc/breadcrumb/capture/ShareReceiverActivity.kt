package com.lbc.breadcrumb.capture

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.data.Memory
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as BreadcrumbApp
        
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        
        if (text.isNullOrBlank()) {
            Toast.makeText(this, getString(com.lbc.breadcrumb.R.string.capture_nothing_to_save), Toast.LENGTH_SHORT).show()
            closeCleanly()
            return
        }

        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val cleanTitle = TextRules.cleanSubject(subject)
        val (type, hasLink) = TextRules.parseText(text)
        val (sourceApp, sourceAppLabel) = SourceHelper.getSource(this)
        
        val memory = Memory(
            type = type,
            hasLink = hasLink,
            rawText = text,
            title = cleanTitle,
            sourceApp = sourceApp,
            sourceAppLabel = sourceAppLabel
        )

        app.applicationScope.launch {
            app.database.memoryDao().upsert(memory)
        }

        Toast.makeText(this, getString(com.lbc.breadcrumb.R.string.capture_saved), Toast.LENGTH_SHORT).show()
        closeCleanly()
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
