package com.lbc.breadcrumb.ocr

import android.content.Context

/**
 * Tells the phone's background work there is something new: a link's page to
 * read, a memory to embed. Called after every save and every read, including
 * one that found nothing: a picture with no words is embedded as a picture.
 *
 * [com.lbc.breadcrumb.BreadcrumbApp.onCreate] points it at [LinkWorker] and
 * [com.lbc.breadcrumb.embed.EmbedWorker] before the OCR queue starts; a no-op
 * until then, and in tests that build the queue alone.
 */
var workScheduler: (Context) -> Unit = {}
