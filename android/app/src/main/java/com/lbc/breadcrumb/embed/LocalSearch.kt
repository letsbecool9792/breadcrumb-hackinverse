package com.lbc.breadcrumb.embed

import com.lbc.breadcrumb.data.EmbeddingDao
import com.lbc.breadcrumb.data.EmbeddingsVersion
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.net.Interpretation
import com.lbc.breadcrumb.net.Ranks
import com.lbc.breadcrumb.net.SearchHit
import com.lbc.breadcrumb.net.SearchOutcome
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId

/**
 * Search on the phone (local-first L2): every memory ranked by meaning against
 * the phrase, fused with the phone's word search by reciprocal rank, and
 * narrowed by the parsed phrase, as the server does it. Only the parse may leave
 * the phone; without it, the same ranking without filters.
 */
class LocalSearch(
    private val embed: suspend (prompt: String) -> FloatArray?,
    private val vectors: suspend () -> List<Pair<String, FloatArray>>,
    private val words: suspend (query: String, limit: Int) -> List<Memory>,
    private val parse: suspend (phrase: String) -> Interpretation?,
    private val memories: suspend () -> List<Memory>,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /** The ranked answer, or null when the model isn't on the phone yet, so the caller can fall back. */
    suspend fun search(phrase: String, limit: Int): SearchOutcome.Found? = coroutineScope {
        // A parse that is slow costs the search only its filters.
        val parsed = async { withTimeoutOrNull(PARSE_WAIT_MILLIS) { runCatching { parse(phrase) }.getOrNull() } }
        val query = embed(EmbedText.query(phrase)) ?: return@coroutineScope null
        val interpretation = parsed.await()
        val held = memories().associateBy { it.id }
        val kept = { id: String -> held[id]?.let { matches(it, interpretation) } == true }

        val scored = vectors().filter { kept(it.first) }.map { (id, vector) -> id to Vectors.dot(query, vector) }
            .sortedByDescending { it.second }
        val best = scored.firstOrNull()?.second ?: 0f
        // Only what is near the best: the rest of a small corpus is noise, not an answer.
        val byMeaning = scored.takeWhile { best - it.second <= MEANING_WINDOW }.take(limit).map { it.first }
        val wordsOf = interpretation?.query?.takeIf { it.isNotBlank() } ?: phrase
        val byWords = words(wordsOf, limit).map { it.id }.filter(kept)

        val hits = fuse(byMeaning, byWords, limit).map { SearchHit(id = it.first, score = it.second, ranks = it.third) }
        SearchOutcome.Found(hits, interpretation)
    }

    /**
     * The parsed filters, each narrowing on its own, as the server's: a link
     * filter takes anything carrying a link; dates are the content's own date,
     * or when it was saved; a source is the app it came from, or a link's site.
     */
    internal fun matches(memory: Memory, interpretation: Interpretation?): Boolean {
        if (interpretation == null) return true
        val types = interpretation.types
        if (types.isNotEmpty()) {
            val ofType = memory.type.name in types || ("LINK" in types && memory.hasLink)
            if (!ofType) return false
        }
        val dated = memory.contentCreatedAt ?: memory.capturedAt
        interpretation.from?.let { from ->
            if (dated < LocalDate.parse(from).atStartOfDay(zone).toInstant().toEpochMilli()) return false
        }
        interpretation.to?.let { to ->
            if (dated >= LocalDate.parse(to).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()) return false
        }
        interpretation.sourceApp?.let { source ->
            val app = memory.sourceAppLabel.equals(source, ignoreCase = true)
            val site = Sites.of(memory.rawText).any { it.equals(source, ignoreCase = true) }
            if (!app && !site) return false
        }
        return true
    }

    companion object {
        /** The constant `$rankFusion` uses, as the server does (architecture rule 5). */
        const val RRF_K = 60

        /** How far below the best match by meaning a memory may score and still be an answer. */
        const val MEANING_WINDOW = 0.12f

        const val PARSE_WAIT_MILLIS = 2_500L

        /** Reciprocal rank fusion, equal weights, ranks 1-based: the server's `fuse`, on the phone. */
        fun fuse(byMeaning: List<String>, byWords: List<String>, limit: Int): List<Triple<String, Double, Ranks>> {
            val score = linkedMapOf<String, Double>()
            val vector = mutableMapOf<String, Int>()
            val text = mutableMapOf<String, Int>()
            byMeaning.forEachIndexed { i, id ->
                score[id] = (score[id] ?: 0.0) + 1.0 / (RRF_K + i + 1)
                vector[id] = i + 1
            }
            byWords.forEachIndexed { i, id ->
                score[id] = (score[id] ?: 0.0) + 1.0 / (RRF_K + i + 1)
                text[id] = i + 1
            }
            return score.entries.sortedByDescending { it.value }.take(limit)
                .map { Triple(it.key, it.value, Ranks(vector = vector[it.key], text = text[it.key])) }
        }
    }
}

/** Every vector, in memory, reloaded only when one was written or went with its memory. */
class VectorIndex(private val dao: EmbeddingDao) {

    private val lock = Mutex()
    private var version: EmbeddingsVersion? = null
    private var vectors: List<Pair<String, FloatArray>> = emptyList()

    suspend fun current(): List<Pair<String, FloatArray>> = lock.withLock {
        val now = dao.version()
        if (now != version) {
            vectors = dao.vectors().map { it.memoryId to Vectors.fromBytes(it.vector) }
            version = now
        }
        vectors
    }
}
