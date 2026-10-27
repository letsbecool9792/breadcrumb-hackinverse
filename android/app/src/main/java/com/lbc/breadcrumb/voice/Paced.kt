package com.lbc.breadcrumb.voice

import java.io.OutputStream

/**
 * Lets bytes through no faster than [bytesPerSecond]: the phone's recognizer
 * reads a pipe as fast as it is filled, and fed much faster than it hears it
 * drops words at the pauses where it ends one segment and starts the next.
 */
internal class Paced(
    private val out: OutputStream,
    private val bytesPerSecond: Long,
    private val now: () -> Long = System::nanoTime,
    private val sleep: (Long) -> Unit = Thread::sleep,
) : OutputStream() {

    private var start = -1L
    private var written = 0L

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (start < 0) start = now()
        var at = off
        val end = off + len
        while (at < end) {
            val piece = minOf(end - at, (bytesPerSecond / 10).toInt().coerceAtLeast(2))
            // Not before this much has had its time.
            val due = written * 1_000_000_000L / bytesPerSecond
            val ahead = (due - (now() - start)) / 1_000_000L
            if (ahead > 0) sleep(ahead)
            out.write(b, at, piece)
            written += piece
            at += piece
        }
    }

    override fun flush() = out.flush()

    override fun close() = out.close()
}
