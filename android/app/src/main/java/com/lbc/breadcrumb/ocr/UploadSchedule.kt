package com.lbc.breadcrumb.ocr

import android.content.Context

/**
 * Tells the sync workstream there is something new to send. Called after every
 * read, including one that found nothing: an empty read is exactly the case
 * whose picture gets sent.
 *
 * [com.lbc.breadcrumb.BreadcrumbApp.onCreate] points it at
 * [com.lbc.breadcrumb.sync.UploadWorker.schedule] before the OCR queue starts;
 * a no-op until then, and in tests that build the queue alone.
 */
var uploadScheduler: (Context) -> Unit = {}
