package com.lbc.breadcrumb

import android.app.Application
import com.lbc.breadcrumb.data.BreadcrumbDatabase
import com.lbc.breadcrumb.data.OriginalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    override fun onCreate() {
        super.onCreate()
        // Work that runs in every process starts here, inside applicationScope
        // so none of it touches the main thread during a capture's cold start:
        // the OCR queue (reading workstream), and the upload queue's first
        // schedule (sync workstream).
    }
}
