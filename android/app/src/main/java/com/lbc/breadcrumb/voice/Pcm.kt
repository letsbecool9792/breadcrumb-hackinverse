package com.lbc.breadcrumb.voice

import kotlin.math.roundToInt

/**
 * Sound as the recognizer takes it: 16 kHz, one channel, 16-bit. Pure, so the
 * JVM tests it; [PcmDecoder] feeds it whatever a file decodes to.
 */
internal object Pcm {

    const val RATE = 16_000

    /** One channel from [channels] interleaved: the mean of each frame's samples. */
    fun mono(samples: ShortArray, count: Int, channels: Int): ShortArray {
        if (channels <= 1) return samples.copyOf(count)
        val frames = count / channels
        return ShortArray(frames) { frame ->
            var sum = 0
            for (c in 0 until channels) sum += samples[frame * channels + c]
            (sum / channels).toShort()
        }
    }

    /** Float samples (-1..1) as 16-bit, clipped. */
    fun fromFloat(samples: FloatArray, count: Int): ShortArray =
        ShortArray(count) { (samples[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort() }

    /** Little-endian bytes, as the recognizer reads them. */
    fun bytes(samples: ShortArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = samples[i].toInt()
            out[2 * i] = s.toByte()
            out[2 * i + 1] = (s shr 8).toByte()
        }
        return out
    }

    /**
     * Linear resampling from [from] Hz to [to], a chunk at a time: it carries the
     * last sample and its place across chunks, so a file decoded in pieces comes
     * out as if resampled whole.
     */
    class Resampler(private val from: Int, private val to: Int) {
        private val step = from.toDouble() / to

        /** Where the next output sample falls, in input samples from the start of the next chunk. */
        private var position = 0.0
        private var previous: Short? = null

        fun push(input: ShortArray): ShortArray {
            if (from == to) return input
            if (input.isEmpty()) return input
            // The last sample of the chunk before sits at index -1.
            val last = previous
            val out = ArrayList<Short>((input.size / step).toInt() + 2)
            while (true) {
                val at = position
                val i = kotlin.math.floor(at).toInt()
                if (i + 1 > input.lastIndex) break
                val a = if (i < 0) last ?: input[0] else input[i]
                val b = input[i + 1]
                val t = at - i
                out += (a + (b - a) * t).roundToInt().toShort()
                position += step
            }
            position -= input.size
            previous = input.last()
            return out.toShortArray()
        }
    }
}
