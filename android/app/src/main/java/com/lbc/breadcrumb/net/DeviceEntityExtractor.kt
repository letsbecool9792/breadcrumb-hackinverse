package com.lbc.breadcrumb.net

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import java.util.concurrent.TimeUnit

/**
 * On-device entity extraction via ML Kit (local-first L4): phone numbers,
 * emails, addresses, cards, IBANs and tracking numbers. Answers null when it
 * could not read the text (the model not downloaded yet, a failure, a timeout),
 * so the memory waits rather than going with only the regexes' stripping.
 */
class DeviceEntityExtractor : EntityExtractor {

    private val client by lazy {
        runCatching {
            EntityExtraction.getClient(
                EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build(),
            )
        }.getOrNull()
    }

    /** On any network: asked for when "Gemini reads my memories" is turned on. */
    override fun prepare() {
        client?.downloadModelIfNeeded()
    }

    override fun extract(text: String): List<RedactionSpan>? {
        val extractor = client ?: return null
        return try {
            // Not downloaded yet: give up quickly; prepare() asked for it, and the pass tries again later.
            Tasks.await(extractor.downloadModelIfNeeded(), READY_SECONDS, TimeUnit.SECONDS)
            EntityChunks.annotate(text) { piece ->
                Tasks.await(extractor.annotate(piece), PIECE_SECONDS, TimeUnit.SECONDS).mapNotNull { annotation ->
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
            }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val READY_SECONDS = 3L
        const val PIECE_SECONDS = 3L
    }
}
