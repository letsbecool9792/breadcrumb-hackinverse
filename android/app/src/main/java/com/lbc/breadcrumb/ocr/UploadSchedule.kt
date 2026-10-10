package com.lbc.breadcrumb.ocr

import android.content.Context

/**
 * Tells the sync workstream there is something new to send. Called after every
 * read, including one that found nothing: an empty read is exactly the case
 * whose picture gets sent.
 *
 * A no-op until the sync workstream points it at `UploadWorker.schedule` (U2).
 */
var uploadScheduler: (Context) -> Unit = {}
