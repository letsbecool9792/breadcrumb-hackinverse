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
 * EmbeddingGemma 2 on the phone, through LiteRT-LM. One engine for the app,
 * started on first use, one call at a time, and closed after two idle minutes
 * or when [close] lets it go.
 *
 * Words always run on the CPU: the engine starts in about 1.5 s, which a search
 * waits for. A picture's vision encoder runs on the GPU where [gpu] allows it
 * (roadmap 6): on the demo phone a picture takes about 2 s there against 4.5 s
 * on the CPU, in about 0.8 GB either way, and its vector is the CPU's to a
 * cosine of 0.998. Words alone start an engine without it; the first picture
 * starts it again with it (about 1.7 s once its compiled kernels are cached in
 * [cacheDir], about 7 s the first time).
 */
class Embedder(
    private val model: () -> File?,
    private val cacheDir: File,
    private val scope: CoroutineScope,
    /** Null keeps everything on the CPU. */
    private val gpu: GpuChoice? = null,
) : Embeddings {

    private val lock = Mutex()
    private var engine: EmbeddingEngine? = null
    private var visionOnGpu = false
    private var idle: Job? = null

    override suspend fun text(prompt: String): FloatArray? = compute(listOf(InputData.Text(prompt)), picture = false)

    override suspend fun picture(jpeg: ByteArray, prompt: String?): FloatArray? =
        compute(listOfNotNull(InputData.Image(jpeg), prompt?.let { InputData.Text(it) }), picture = true)

    /** Lets go of the model and its memory: after a pass over pictures, which takes it to about 0.8 GB. */
    suspend fun close() = lock.withLock { shut() }

    private fun shut() {
        idle?.cancel()
        engine?.close()
        engine = null
        visionOnGpu = false
    }

    private suspend fun compute(inputs: List<InputData>, picture: Boolean): FloatArray? = withContext(Dispatchers.Default) {
        lock.withLock {
            idle?.cancel()
            // A picture wants the GPU where it works: an engine started for words alone makes way.
            if (picture && engine != null && !visionOnGpu && gpu?.allowed() == true) shut()
            val running = engine ?: start(picture) ?: return@withLock null
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

    /** An engine, with the vision encoder on the GPU when it is for a picture and the GPU works here. */
    private fun start(picture: Boolean): EmbeddingEngine? {
        val file = model() ?: return null
        if (picture && gpu?.allowed() == true) {
            gpu.starting()
            try {
                return engine(file, Backend.GPU()).also {
                    gpu.worked()
                    visionOnGpu = true
                }
            } catch (_: Exception) {
                // No OpenCL, or a GPU the model doesn't run on: pictures go to the CPU from now on.
                gpu.failed()
            }
        }
        return engine(file, Backend.CPU())
    }

    private fun engine(file: File, vision: Backend): EmbeddingEngine {
        val config = EmbeddingEngineConfig(
            modelPath = file.path,
            backend = Backend.CPU(),
            visionBackend = vision,
            cacheDir = cacheDir.path,
        )
        val made = EmbeddingEngine(config)
        try {
            made.initialize()
        } catch (e: Exception) {
            runCatching { made.close() }
            throw e
        }
        engine = made
        return made
    }

    private companion object {
        val OPTIONS = EmbeddingOptions(normalize = true)
        const val IDLE_MILLIS = 2 * 60_000L
    }
}
