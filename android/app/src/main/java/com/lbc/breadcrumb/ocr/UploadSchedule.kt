package com.lbc.breadcrumb.ocr

import android.content.Context

/**
 * Notifies the sync workstream that a memory has been read and is ready to upload.
 *
 * Calls `UploadWorker.schedule(context)` once the sync workstream lands it on main.
 * Until then, defaults to a no-op callback.
 */
var uploadScheduler: (Context) -> Unit = { context ->
    try {
        val clazz = Class.forName("com.lbc.breadcrumb.sync.UploadWorker")
        val method = clazz.getMethod("schedule", Context::class.java)
        method.invoke(null, context)
    } catch (_: Throwable) {
        // No-op until UploadWorker is on main
    }
}
