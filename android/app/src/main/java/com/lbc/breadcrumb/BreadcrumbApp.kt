package com.lbc.breadcrumb

import android.app.Application
import com.lbc.breadcrumb.data.BreadcrumbDatabase
import com.lbc.breadcrumb.data.OriginalStore
import com.lbc.breadcrumb.embed.EmbedWorker
import com.lbc.breadcrumb.embed.Embedder
import com.lbc.breadcrumb.embed.ModelStore
import com.lbc.breadcrumb.imports.BulkImporter
import com.lbc.breadcrumb.ocr.LinkWorker
import com.lbc.breadcrumb.ocr.OcrQueue
import com.lbc.breadcrumb.ocr.workScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class BreadcrumbApp : Application() {

    /**
     * Work that must outlive the Activity that started it.
     *
     * Capture surfaces finish within milliseconds of launching -- that is the
     * point of them -- so a `lifecycleScope` coroutine would be cancelled
     * before the insert ever reached Room. SupervisorJob so one failed save
     * cannot take the scope down with it.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The one database. Built on first use, never on the main thread at a cold start. */
    val database: BreadcrumbDatabase get() = BreadcrumbDatabase.get(this)

    /** Where the originals of saved pictures and PDFs live (architecture rule 1). */
    val originals: OriginalStore by lazy { OriginalStore(this) }

    /** The embedding model's file, downloaded once (local-first L1). */
    val models: ModelStore by lazy { ModelStore(this) }

    /** EmbeddingGemma 2 on the phone: memories are embedded, and searched, here (local-first L1). */
    val embedder: Embedder by lazy { Embedder(models::ready, cacheDir, applicationScope) }

    /** Bulk importer for bringing in content already on the phone (reading R5). */
    val importer: BulkImporter by lazy { BulkImporter(this) }

    /**
     * Reading workstream: true while the OCR queue is working through unread
     * pictures and PDFs. Presumed true until the queue has looked, and goes
     * false in a finally when the queue finishes or never starts. The upload
     * pass held unread pictures back on it; since local-first L7 nothing reads it.
     */
    val readingOriginals = MutableStateFlow(true)

    override fun onCreate() {
        super.onCreate()
        // Before the OCR queue starts: a read landing first would call the
        // no-op, and nothing would be scheduled.
        workScheduler = {
            LinkWorker.schedule(it)
            // What was saved or read is embedded on the phone (local-first L1).
            EmbedWorker.schedule(it)
        }
        // Work that runs in every process starts here, inside applicationScope
        // so none of it touches the main thread during a capture's cold start:
        // the OCR queue (reading workstream), and the first schedule of links'
        // pages and embedding, for whatever a killed process left. Building the
        // queue builds the database.
        OcrQueue.launchIn(applicationScope, readingOriginals) { OcrQueue(this) }
        applicationScope.launch {
            LinkWorker.schedule(this@BreadcrumbApp)
            EmbedWorker.schedule(this@BreadcrumbApp)
        }
    }
}
