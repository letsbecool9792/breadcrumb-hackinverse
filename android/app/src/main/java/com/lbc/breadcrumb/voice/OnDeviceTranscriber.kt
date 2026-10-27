package com.lbc.breadcrumb.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/**
 * The phone's own speech recognizer, offline only (`createOnDeviceSpeechRecognizer`,
 * Android 13+), fed the file's sound through a pipe rather than the microphone
 * (`EXTRA_AUDIO_SOURCE`). Nothing leaves the phone. Its language is the phone's,
 * or one of the same language the phone has installed ("en-US" for "en-IN").
 *
 * Every call into the recognizer is on the main thread, as it requires.
 */
class OnDeviceTranscriber(private val context: Context) : Transcriber {

    override suspend fun transcribe(file: File): Heard {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return Heard.Never
        // Long enough for the whole file at speaking pace, and then some.
        val limit = withContext(Dispatchers.IO) { (AudioLength.of(file) ?: DEFAULT_LENGTH_MS) * 2 + 30_000 }
        return withContext(Dispatchers.Main) {
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return@withContext Heard.Never
            // One recognizer for the whole of it: destroying one while the next connects
            // unbinds the recognizer's service, and the second hears nothing (error 11).
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            try {
                when (val language = language(recognizer)) {
                    is Language.Use -> listen(recognizer, file, language.tag, limit)
                    is Language.Wait -> Heard.NotNow("the recognizer is getting ${language.tag}")
                    Language.None -> Heard.Never
                }
            } finally {
                recognizer.destroy()
            }
        }
    }

    private sealed interface Language {
        /** Listen in [tag]; null when the recognizer couldn't say what it has, so it chooses. */
        data class Use(val tag: String?) : Language
        data class Wait(val tag: String) : Language
        data object None : Language
    }

    /** The phone's language if installed, else an installed one of the same language; else its download is asked for. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun language(recognizer: SpeechRecognizer): Language {
        val wanted = Locale.getDefault()
        val support = support(recognizer) ?: return Language.Use(wanted.toLanguageTag())
        val same = { tag: String -> Locale.forLanguageTag(tag).language == wanted.language }
        support.installedOnDeviceLanguages.firstOrNull { it.equals(wanted.toLanguageTag(), ignoreCase = true) }
            ?.let { return Language.Use(it) }
        support.installedOnDeviceLanguages.firstOrNull(same)?.let { return Language.Use(it) }
        support.pendingOnDeviceLanguages.firstOrNull(same)?.let { return Language.Wait(it) }
        val download = support.supportedOnDeviceLanguages.firstOrNull { it.equals(wanted.toLanguageTag(), ignoreCase = true) }
            ?: support.supportedOnDeviceLanguages.firstOrNull(same)
            ?: return Language.None
        recognizer.triggerModelDownload(intent(null, download))
        return Language.Wait(download)
    }

    /** What the recognizer has and can get; null when it can't say. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun support(recognizer: SpeechRecognizer): RecognitionSupport? =
        withTimeoutOrNull(SUPPORT_TIMEOUT_MS) {
            suspendCancellableCoroutine { done ->
                recognizer.checkRecognitionSupport(
                    intent(null, null),
                    ContextCompat.getMainExecutor(context),
                    object : RecognitionSupportCallback {
                        override fun onSupportResult(support: RecognitionSupport) {
                            if (done.isActive) done.resume(support)
                        }

                        override fun onError(error: Int) {
                            if (done.isActive) done.resume(null)
                        }
                    },
                )
            }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun listen(recognizer: SpeechRecognizer, file: File, language: String?, limit: Long): Heard = withContext(Dispatchers.Main) {
        val (source, sink) = ParcelFileDescriptor.createPipe()
        // The file's sound, decoded into the pipe at a pace the recognizer keeps up with.
        val feeding = launch(Dispatchers.IO) {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(sink).use { pipe ->
                    PcmDecoder.decode(file, Paced(pipe, Pcm.RATE * 2L * SPEED))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Undecodable, or the recognizer stopped reading: the pipe closes, and it has heard what it heard.
            }
        }
        val segments = mutableListOf<String>()
        try {
            withTimeoutOrNull(limit) {
                suspendCancellableCoroutine<Heard> { done ->
                    fun finish(heard: Heard) {
                        if (done.isActive) done.resume(heard)
                    }
                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onSegmentResults(segmentResults: Bundle) {
                            best(segmentResults)?.let(segments::add)
                        }

                        override fun onEndOfSegmentedSession() = finish(Heard.Words(joined(segments)))

                        override fun onResults(results: Bundle) {
                            best(results)?.let(segments::add)
                            finish(Heard.Words(joined(segments)))
                        }

                        override fun onError(error: Int) = finish(outcomeOf(error, segments))

                        override fun onReadyForSpeech(params: Bundle?) = Unit
                        override fun onBeginningOfSpeech() = Unit
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() = Unit
                        override fun onPartialResults(partialResults: Bundle?) = Unit
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    })
                    recognizer.startListening(intent(source, language))
                }
            } ?: Heard.NotNow("took too long")
        } finally {
            feeding.cancel()
            recognizer.cancel()
            runCatching { source.close() }
        }
    }

    private fun best(results: Bundle): String? =
        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    private fun joined(segments: List<String>): String =
        segments.joinToString(" ").replace(Regex("""\s+"""), " ").trim()

    private fun outcomeOf(error: Int, segments: List<String>): Heard = when (error) {
        // Nothing (more) that could be made out: what was heard is the note's words.
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Heard.Words(joined(segments))
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> Heard.Never
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> Heard.NotNow("its language isn't on the phone yet")
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Heard.NotNow("breadcrumb isn't on screen")
        else -> Heard.NotNow("recognizer error $error")
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun intent(source: ParcelFileDescriptor?, language: String?) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Offline only: nothing said leaves the phone.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            language?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
            if (source != null) {
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, source)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, Pcm.RATE)
                // The whole file, in segments, not just up to the first pause.
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
            }
        }

    private companion object {
        const val SUPPORT_TIMEOUT_MS = 5_000L
        const val DEFAULT_LENGTH_MS = 10 * 60_000L

        /**
         * How many times faster than it was spoken. Fed as fast as it reads (about 6x on the
         * demo phone) it lost words at a pause in two runs of three; at 3x, none in ten.
         */
        const val SPEED = 3
    }
}
