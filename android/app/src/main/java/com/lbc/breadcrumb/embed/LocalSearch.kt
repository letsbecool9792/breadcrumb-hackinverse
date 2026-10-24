package com.lbc.breadcrumb.embed

import com.lbc.breadcrumb.data.EmbeddingDao
import com.lbc.breadcrumb.data.EmbeddingsVersion
import com.lbc.breadcrumb.data.Memory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Search on the phone (local-first L2): every memory ranked by meaning against
 * the phrase, fused with the phone's word search by reciprocal rank, and
 * narrowed by the phrase's filters, which are taken apart on the phone too
 * (L6, [PhraseRules]). Nothing typed leaves the phone.
 */
class LocalSearch(
    private val embed: suspend (prompt: String) -> FloatArray?,
    private val vectors: suspend () -> List<Pair<String, FloatArray>>,
    private val words: suspend (query: String, limit: Int) -> List<Memory>,
    private val parse: suspend (phrase: String) -> Interpretation?,
    private val memories: suspend () -> List<Memory>,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /** The first answer, at once: the phrase's words, narrowed by its filters. */
    suspend fun byWords(phrase: String, limit: Int): List<Memory> {
        val interpretation = parse(phrase)
        filtersAlone(interpretation, limit)?.let { return it }
        return words(wordsOf(phrase, interpretation), limit).filter { matches(it, interpretation) }
    }

    /** The ranked answer, by meaning and words; by words alone until the model is on the phone. */
    suspend fun search(phrase: String, limit: Int): SearchOutcome.Found {
        val interpretation = parse(phrase)
        filtersAlone(interpretation, limit)?.let { kept ->
            return SearchOutcome.Found(kept.map { SearchHit(id = it.id) }, interpretation)
        }
        val held = memories().associateBy { it.id }
        val kept = { id: String -> held[id]?.let { matches(it, interpretation) } == true }

        val byMeaning = embed(EmbedText.query(phrase))?.let { query ->
            val scored = vectors().filter { kept(it.first) }.map { (id, vector) -> id to Vectors.dot(query, vector) }
                .sortedByDescending { it.second }
            val best = scored.firstOrNull()?.second ?: 0f
            // Only what is near the best: the rest of a small corpus is noise, not an answer.
            scored.takeWhile { best - it.second <= MEANING_WINDOW }.take(limit).map { it.first }
        }.orEmpty()
        val byWords = words(wordsOf(phrase, interpretation), limit).map { it.id }.filter(kept)

        val hits = fuse(byMeaning, byWords, limit).map { SearchHit(id = it.first, score = it.second, ranks = it.third) }
        return SearchOutcome.Found(hits, interpretation)
    }

    private fun wordsOf(phrase: String, interpretation: Interpretation?) =
        interpretation?.query?.takeIf { it.isNotBlank() } ?: phrase

    /**
     * A phrase of filters alone ("screenshots from last week") asks for
     * everything they keep, newest first, not the few nearest a meaning.
     */
    private suspend fun filtersAlone(interpretation: Interpretation?, limit: Int): List<Memory>? {
        if (interpretation == null || interpretation.query.isNotBlank()) return null
        if (interpretation.types.isEmpty() && interpretation.from == null && interpretation.to == null && interpretation.sourceApp == null) {
            return null
        }
        return memories().filter { matches(it, interpretation) }
            .sortedByDescending { it.contentCreatedAt ?: it.capturedAt }
            .take(limit)
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
