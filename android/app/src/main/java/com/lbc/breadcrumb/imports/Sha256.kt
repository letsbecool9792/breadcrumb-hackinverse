package com.lbc.breadcrumb.imports

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

object Sha256 {
    private const val BUFFER_SIZE = 8192

    /**
     * Computes the SHA-256 hash of an [InputStream].
     */
    fun digest(inputStream: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            md.update(buffer, 0, bytesRead)
        }
        return toHex(md.digest())
    }

    /**
     * Computes the SHA-256 hash of a [File].
     */
    fun digest(file: File): String {
        return file.inputStream().use { digest(it) }
    }

    /**
     * Copies all bytes from [input] to [output] while simultaneously computing
     * the SHA-256 checksum of the stream. Returns the hex string of the SHA-256 hash.
     */
    fun copyAndDigest(input: InputStream, output: OutputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            md.update(buffer, 0, bytesRead)
        }
        output.flush()
        return toHex(md.digest())
    }

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}
