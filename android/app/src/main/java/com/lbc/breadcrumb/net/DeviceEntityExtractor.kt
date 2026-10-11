package com.lbc.breadcrumb.net

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import java.util.concurrent.TimeUnit

/**
 * On-device entity extraction via ML Kit (local-first L4):
 * extracts phone numbers, emails, addresses, cards, IBANs, and tracking numbers.
 */
class DeviceEntityExtractor : EntityExtractor {

    private val client by lazy {
        runCatching {
            EntityExtraction.getClient(
                EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build(),
            )
        }.getOrNull()
    }

    override fun extract(text: String): List<RedactionSpan> {
        val extractor = client ?: return emptyList()
        return try {
            Tasks.await(extractor.downloadModelIfNeeded(), 3, TimeUnit.SECONDS)
            val annotations = Tasks.await(extractor.annotate(text), 3, TimeUnit.SECONDS)
            annotations.mapNotNull { annotation ->
                val tag = when {
                    annotation.entities.any { it.type == Entity.TYPE_PHONE } -> "[phone]"
                    annotation.entities.any { it.type == Entity.TYPE_EMAIL } -> "[email]"
                    annotation.entities.any { it.type == Entity.TYPE_ADDRESS } -> "[address]"
                    annotation.entities.any { it.type == Entity.TYPE_PAYMENT_CARD } -> "[card]"
                    annotation.entities.any { it.type == Entity.TYPE_IBAN } -> "[iban]"
                    annotation.entities.any { it.type == Entity.TYPE_TRACKING_NUMBER } -> "[tracking]"
                    else -> null // dates, URLs, etc. stay intact
                }
                tag?.let { RedactionSpan(annotation.start, annotation.end, it) }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
