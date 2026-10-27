package com.lbc.breadcrumb.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class PcmTest {

    @Test
    fun `two channels become one, their mean`() {
        assertArrayEquals(shortArrayOf(15, -5), Pcm.mono(shortArrayOf(10, 20, -10, 0), 4, 2))
        assertArrayEquals(shortArrayOf(1, 2), Pcm.mono(shortArrayOf(1, 2, 3), 2, 1))
    }

    @Test
    fun `float samples become sixteen bits, clipped`() {
        assertArrayEquals(shortArrayOf(0, Short.MAX_VALUE, (-Short.MAX_VALUE).toShort()), Pcm.fromFloat(floatArrayOf(0f, 2f, -1f), 3))
    }

    @Test
    fun `samples go little-endian`() {
        assertArrayEquals(byteArrayOf(0x34, 0x12, 0xFF.toByte(), 0xFF.toByte()), Pcm.bytes(shortArrayOf(0x1234, -1)))
    }

    @Test
    fun `resampled in chunks it is as if resampled whole`() {
        val ramp = ShortArray(4_800) { (it % 1_000).toShort() }
        val whole = Pcm.Resampler(48_000, 16_000).push(ramp)
        val chunked = Pcm.Resampler(48_000, 16_000).let { r ->
            listOf(ramp.copyOfRange(0, 1_000), ramp.copyOfRange(1_000, 1_001), ramp.copyOfRange(1_001, 4_800))
                .flatMap { r.push(it).toList() }
        }
        assertEquals(1_600, whole.size)
        assertEquals(whole.toList(), chunked)
        // Every third sample, from the first.
        assertEquals(listOf<Short>(0, 3, 6), whole.take(3))
    }

    @Test
    fun `up from a lower rate falls between samples`() {
        val out = Pcm.Resampler(8_000, 16_000).push(shortArrayOf(0, 100, 200))
        assertEquals(listOf<Short>(0, 50, 100, 150), out.toList())
    }

    @Test
    fun `paced bytes go no faster than asked, in pieces of a tenth of a second`() {
        var clock = 0L
        val slept = mutableListOf<Long>()
        val out = ByteArrayOutputStream()
        val paced = Paced(out, bytesPerSecond = 1_000, now = { clock }, sleep = { ms -> slept += ms; clock += ms * 1_000_000 })

        paced.write(ByteArray(350), 0, 350)

        assertEquals(350, out.size())
        // 100 bytes are due at once, then one more 100 every 100 ms.
        assertEquals(listOf(100L, 100L, 100L), slept)
    }
}
