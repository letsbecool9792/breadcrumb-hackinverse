package com.lbc.breadcrumb.imports

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class Sha256Test {

    @Test
    fun `computes correct sha256 hash for byte stream`() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        val emptyDigest = Sha256.digest(ByteArrayInputStream(ByteArray(0)))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyDigest)

        // SHA-256("breadcrumb") = d6dc6b5ebe192c2ce1c052797a0907948cf02874ef63d2b2ac40c2380ab90c95
        val textBytes = "breadcrumb".toByteArray(Charsets.UTF_8)
        val textDigest = Sha256.digest(ByteArrayInputStream(textBytes))
        assertEquals("d6dc6b5ebe192c2ce1c052797a0907948cf02874ef63d2b2ac40c2380ab90c95", textDigest)
    }

    @Test
    fun `copyAndDigest copies identical bytes and returns correct hash`() {
        val inputData = "hello world from breadcrumb importer".toByteArray(Charsets.UTF_8)
        val input = ByteArrayInputStream(inputData)
        val output = ByteArrayOutputStream()

        val digest = Sha256.copyAndDigest(input, output)

        val expectedDigest = Sha256.digest(ByteArrayInputStream(inputData))
        assertEquals(expectedDigest, digest)
        assertEquals(inputData.toList(), output.toByteArray().toList())
    }
}
