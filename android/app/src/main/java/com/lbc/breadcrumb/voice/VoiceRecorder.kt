package com.lbc.breadcrumb.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID
import kotlin.math.sqrt

/** A voice note just recorded: its file in the cache, until it is kept. */
class Recording(val id: String, val file: File, val startedAt: Long, val lengthMs: Long)

/**
 * Records a voice note from the microphone: AAC in an MPEG-4 file (.m4a), one
 * channel at 44.1 kHz and 64 kbps, about half a megabyte a minute. It goes to
 * the cache, and the original store takes it once it is kept.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var recording: Pair<String, File>? = null
    private var startedAt = 0L

    /** Starts recording; false when the microphone can't be had. [onLimit] when it reaches [MAX_MS]. */
    fun start(onLimit: () -> Unit): Boolean {
        val id = UUID.randomUUID().toString()
        val file = File(File(context.cacheDir, "voice").apply { mkdirs() }, "$id.m4a")
        val made = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            made.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(64_000)
                setMaxDuration(MAX_MS.toInt())
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onLimit()
                }
                setOutputFile(file.path)
                prepare()
                start()
            }
            recorder = made
            recording = id to file
            startedAt = System.currentTimeMillis()
            true
        } catch (_: Exception) {
            made.release()
            file.delete()
            false
        }
    }

    /** How loud since it was last asked, 0 to 1, on a square-root scale so speech fills it. */
    fun level(): Float {
        val amplitude = try {
            recorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
        return sqrt(amplitude / 32_767f).coerceIn(0f, 1f)
    }

    /** Stops, and hands back what was recorded; null when it was too short to keep, or failed. */
    fun stop(): Recording? {
        val made = recorder ?: return null
        val (id, file) = recording ?: return null
        recorder = null
        recording = null
        val length = System.currentTimeMillis() - startedAt
        val stopped = try {
            made.stop()
            true
        } catch (_: RuntimeException) {
            // Stopped before anything was written.
            false
        } finally {
            made.release()
        }
        if (!stopped || length < MIN_MS || file.length() == 0L) {
            file.delete()
            return null
        }
        return Recording(id, file, startedAt, length)
    }

    /** Stops and throws away what was recorded. */
    fun discard() {
        stop()?.file?.delete()
    }

    companion object {
        /** Shorter than this is a tap, not a note. */
        const val MIN_MS = 800L

        /** Half an hour, then it is kept and the recording stops. */
        const val MAX_MS = 30 * 60_000L
    }
}
