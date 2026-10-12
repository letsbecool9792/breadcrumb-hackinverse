package com.lbc.breadcrumb.sync

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.ServerReading
import com.lbc.breadcrumb.data.UploadDao
import com.lbc.breadcrumb.net.IngestResult
import com.lbc.breadcrumb.net.Reply
import com.lbc.breadcrumb.net.UploadApi
import com.lbc.breadcrumb.net.UploadPicture
import com.lbc.breadcrumb.net.Redactor
import com.lbc.breadcrumb.net.toUpload
import java.util.Base64

/**
 * One run of the upload queue, in the contract's order (docs/api.md, "What
 * the phone sends"): pending deletes; then links' pages, read on the phone
 * (reading R4); then memories, oldest first, ten per
 * request; then pictures OCR could barely read, four per request; then a
 * copy of what the server made of them. Each step goes until it has nothing
 * left, asking Room again after every request, so a save made meanwhile goes
 * in the same pass.
 *
 * Over [UploadDao] and [UploadApi], so it runs on the phone in tests without
 * a server. [UploadWorker] runs it.
 */
class UploadPass(
    private val dao: UploadDao,
    private val api: UploadApi,
    /** Whether memories may leave the phone (local-first L4 setting). */
    private val cloudAllowed: () -> Boolean = { false },
    /** Ids the capture sheet holds while its note field is open. */
    private val held: () -> Collection<String> = { emptyList() },
    /** True while the OCR queue is working through unread pictures. */
    private val reading: () -> Boolean = { false },
    /** A picture as it is sent ([Pictures.jpeg]), or null when there is none to send. */
    private val pictureOf: suspend (Memory) -> ByteArray? = { null },
    /** Reads unread links' pages ([com.lbc.breadcrumb.ocr.LinkReading]), so each goes with its page's words. */
    private val readLinks: suspend () -> Unit = {},
    /** A "forget everything" waiting to reach the server ([SyncSettings.forgetPending]). */
    private val forgetPending: () -> Boolean = { false },
    /** The server answered the forget: it is done. */
    private val forgotten: () -> Unit = {},
    /** A memory's words as they go, stripped of personal details; null when they couldn't be checked, and then they don't go. */
    private val redact: (String) -> String? = Redactor::forUpload,
    private val now: () -> Long = System::currentTimeMillis,
) {
    sealed interface Outcome {
        /**
         * Everything that could go went. [recheckAt]: when an unread picture
         * or PDF held back now may go, if there is one.
         */
        data class Done(val recheckAt: Long?) : Outcome

        /** The server or the network said not now: run again, with backoff. */
        data class Later(val why: String) : Outcome
    }

    suspend fun run(): Outcome {
        // Nothing is in flight when a pass starts: an UPLOADING row is one a killed process left.
        dao.releaseStale()
        // Reading was turned off: the server forgets everything first. It is the one call made while the
        // setting is off, and until the server answers it, nothing else goes.
        if (forgetPending()) {
            when (val reply = api.forgetAll()) {
                is Reply.Done -> forgotten()
                is Reply.Later -> return Outcome.Later(reply.why)
                is Reply.Refused -> return Outcome.Later("the server refused to forget (${reply.code})")
            }
        }
        // Off, the server holds nothing to delete from, so deletes wait (local first).
        if (cloudAllowed()) sendDeletes()?.let { return it }
        // A link's page is read on the phone even with the setting off, since local search embeds its words (local-first L4).
        readLinks()
        // While the setting is off, nothing else goes: no memories, no pictures, no deletes (local-first L4).
        if (!cloudAllowed()) return Outcome.Done(null)
        // Pictures never go, setting or not (local-first L4): skips sendPictures().
        return sendMemories()
            ?: copyEnrichment()
            ?: Outcome.Done(recheckAt())
    }

    private fun unreadSince() = now() - GRACE_MILLIS

    /** First, so a delete can never land after the memory it removes was sent again. */
    private suspend fun sendDeletes(): Outcome.Later? {
        while (true) {
            val ids = dao.pendingDeletes(DELETES_PER_REQUEST)
            if (ids.isEmpty()) return null
            when (val reply = api.sendDeletes(ids)) {
                is Reply.Later -> return Outcome.Later(reply.why)
                // A refused delete would be refused again: forgotten, so the pass cannot loop on it.
                is Reply.Done, is Reply.Refused -> dao.forgetDeletes(ids)
            }
        }
    }

    private suspend fun sendMemories(): Outcome.Later? {
        while (true) {
            val batch = dao.claimNext(held().toList(), reading(), unreadSince(), MEMORIES_PER_REQUEST)
            if (batch.isEmpty()) return null

            // Banking, UPI, and password manager saves never go (local-first L4).
            val (sensitive, allowed) = batch.partition { com.lbc.breadcrumb.net.SensitiveApps.isSensitive(it.sourceApp) }
            if (sensitive.isNotEmpty()) {
                val sensitiveIds = sensitive.map { it.id }
                dao.markSynced(sensitiveIds)
                dao.markEnriched(sensitiveIds, now())
            }
            if (allowed.isEmpty()) continue

            val ids = allowed.map { it.id }
            val uploads = allowed.map { it.toUpload(redact) }
            if (uploads.any { it == null }) {
                // Redaction fails closed: the phone couldn't check these for personal details, so none go this pass.
                dao.markPending(ids)
                return Outcome.Later("personal details could not be checked yet")
            }
            when (val reply = api.sendMemories(uploads.filterNotNull())) {
                is Reply.Done -> if (!settle(ids, reply.body)) return Outcome.Later("not every memory was finished")
                is Reply.Later -> {
                    settle(ids, reply.body.orEmpty())
                    return Outcome.Later(reply.why)
                }
                is Reply.Refused -> {
                    // Only the ones the server named are wrong. The rest were not stored, and go again.
                    val wrong = named(ids, reply)
                    dao.markFailed(wrong)
                    dao.markPending(ids - wrong.toSet())
                }
            }
        }
    }

    /**
     * Marks what the server stored and finished as synced, and puts the rest
     * back in the queue: a retryable one is stored, but its re-send is what
     * enriches it. Whether every one was finished.
     */
    private suspend fun settle(ids: List<String>, results: List<IngestResult>): Boolean {
        val (synced, back) = finished(ids, results)
        dao.markSynced(synced)
        dao.markPending(back)
        return back.isEmpty()
    }

    private suspend fun sendPictures(): Outcome.Later? {
        while (true) {
            val candidates = dao.picturesToSend(MIN_OCR_CHARS, reading(), unreadSince(), PICTURES_PER_REQUEST)
            if (candidates.isEmpty()) return null
            val pictures = candidates.mapNotNull { memory ->
                pictureOf(memory)?.let { UploadPicture(memory.id, "image/jpeg", Base64.getEncoder().encodeToString(it)) }
            }
            val ids = pictures.map { it.id }
            // A picture the phone cannot read back (gone, or not a picture) would fail every time: never tried again.
            dao.markPicturesSent(candidates.map { it.id } - ids.toSet(), now())
            if (pictures.isEmpty()) continue
            when (val reply = api.sendPictures(pictures)) {
                is Reply.Done -> {
                    val (sent, back) = finished(ids, reply.body)
                    dao.markPicturesSent(sent, now())
                    if (back.isNotEmpty()) return Outcome.Later("not every picture was finished")
                }
                is Reply.Later -> {
                    dao.markPicturesSent(finished(ids, reply.body.orEmpty()).first, now())
                    return Outcome.Later(reply.why)
                }
                is Reply.Refused -> dao.markPicturesSent(named(ids, reply), now())
            }
        }
    }

    /** [ids] split into those the server finished, and those to send again. */
    private fun finished(ids: List<String>, results: List<IngestResult>): Pair<List<String>, List<String>> {
        val finished = results.filterNot { it.retryable }.map { it.id }.toSet()
        return ids.partition { it in finished }
    }

    /** The ids a refusal named, or all of them when it named none it could: never none, or the pass would loop. */
    private fun named(ids: List<String>, refusal: Reply.Refused): List<String> =
        refusal.invalid.mapNotNull(ids::getOrNull).ifEmpty { ids }

    private suspend fun copyEnrichment(): Outcome.Later? {
        while (true) {
            val ids = dao.toEnrich(ENRICHMENT_PER_REQUEST)
            if (ids.isEmpty()) return null
            when (val reply = api.askEnrichment(ids)) {
                is Reply.Later -> return Outcome.Later(reply.why)
                is Reply.Done -> dao.copyEnrichment(
                    asked = ids,
                    readings = reply.body.filter { it.id in ids }.map { ServerReading(it.id, it.summary, it.kind, it.readText) },
                    now = now(),
                )
                is Reply.Refused -> dao.copyEnrichment(ids, emptyList(), now())
            }
        }
    }

    /**
     * When to look again for an unread picture or PDF still held back: once
     * the oldest one's grace is over, since a read that fails tells nobody.
     * Never sooner than a minute, so a long OCR run is not polled.
     */
    private suspend fun recheckAt(): Long? {
        val oldest = dao.oldestUnreadWaiting() ?: return null
        return maxOf(oldest + GRACE_MILLIS, now() + MIN_RECHECK_MILLIS)
    }

    companion object {
        const val DELETES_PER_REQUEST = 100
        const val MEMORIES_PER_REQUEST = 10
        const val PICTURES_PER_REQUEST = 4
        const val ENRICHMENT_PER_REQUEST = 50

        /** A picture is sent only when OCR read fewer characters than this. */
        const val MIN_OCR_CHARS = 80

        /** How long an unread picture or PDF waits once the OCR queue has stopped. */
        const val GRACE_MILLIS = 10 * 60_000L
        const val MIN_RECHECK_MILLIS = 60_000L
    }
}
