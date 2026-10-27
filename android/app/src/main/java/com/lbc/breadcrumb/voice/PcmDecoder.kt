package com.lbc.breadcrumb.voice

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteOrder

/**
 * Decodes a sound file (a recording's AAC, WhatsApp's Opus, an MP3, a WAV:
 * whatever the phone's decoders read) into [Pcm.RATE] mono 16-bit samples,
 * written to [out] as they come, so an hour-long file never sits in memory.
 */
internal object PcmDecoder {

    fun decode(file: File, out: OutputStream) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("no sound in ${file.name}")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            var output = Output(format)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    output = Output(decoder.outputFormat)
                } else if (index >= 0) {
                    val buffer = decoder.getOutputBuffer(index)!!
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val chunk = output.samples(buffer.slice().order(ByteOrder.LITTLE_ENDIAN))
                    if (chunk.isNotEmpty()) out.write(Pcm.bytes(chunk))
                    decoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    /** What the decoder hands out, and how it becomes the recognizer's samples. */
    private class Output(format: MediaFormat) {
        private val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        private val float = format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
            format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
        private val resampler = Pcm.Resampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), Pcm.RATE)

        fun samples(bytes: java.nio.ByteBuffer): ShortArray {
            val interleaved = if (float) {
                val floats = bytes.asFloatBuffer()
                val array = FloatArray(floats.remaining()).also { floats.get(it) }
                Pcm.fromFloat(array, array.size)
            } else {
                val shorts = bytes.asShortBuffer()
                ShortArray(shorts.remaining()).also { shorts.get(it) }
            }
            return resampler.push(Pcm.mono(interleaved, interleaved.size, channels))
        }
    }

    private const val TIMEOUT_US = 10_000L
}
