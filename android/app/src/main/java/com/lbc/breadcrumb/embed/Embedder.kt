package com.lbc.breadcrumb.embed

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** What [EmbedPass] and the ranking need from the model; null when the model isn't on the phone yet. */
interface Embeddings {
    suspend fun text(prompt: String): FloatArray?
    suspend fun picture(jpeg: ByteArray, prompt: String?): FloatArray?
}

/**
 * EmbeddingGemma 2 on the phone's CPU, through LiteRT-LM. One engine for the
 * app, started on first use (about 2 s, about 210 MB), one call at a time, and
 * closed after two idle minutes or when [close] lets it go.
 */
class Embedder(
    private val model: () -> File?,
    private val cacheDir: File,
    private val scope: CoroutineScope,
) : Embeddings {

    private val lock = Mutex()
    private var engine: EmbeddingEngine? = null
    private var idle: Job? = null

    override suspend fun text(prompt: String): FloatArray? = compute(listOf(InputData.Text(prompt)))

    override suspend fun picture(jpeg: ByteArray, prompt: String?): FloatArray? =
        compute(listOfNotNull(InputData.Image(jpeg), prompt?.let { InputData.Text(it) }))

    /** Lets go of the model and its memory: after a pass over pictures, which takes it to about 1 GB. */
    suspend fun close() = lock.withLock {
        idle?.cancel()
        engine?.close()
        engine = null
    }

    private suspend fun compute(inputs: List<InputData>): FloatArray? = withContext(Dispatchers.Default) {
        lock.withLock {
            idle?.cancel()
            val running = engine ?: start() ?: return@withLock null
            try {
                running.computeEmbedding(inputs, OPTIONS).embedding
            } finally {
                idle = scope.launch {
                    delay(IDLE_MILLIS)
                    close()
                }
            }
        }
    }

    private fun start(): EmbeddingEngine? {
        val file = model() ?: return null
        val config = EmbeddingEngineConfig(
            modelPath = file.path,
            backend = Backend.CPU(),
            visionBackend = Backend.CPU(),
            cacheDir = cacheDir.path,
        )
        return EmbeddingEngine(config).also {
            it.initialize()
            engine = it
        }
    }

    private companion object {
        val OPTIONS = EmbeddingOptions(normalize = true)
        const val IDLE_MILLIS = 2 * 60_000L
    }
}
