package com.lbc.breadcrumb.ocr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test

class OcrQueueStartTest {

    @Test
    fun `a queue that cannot be built still lets the uploader go`() = runBlocking {
        val readingOriginals = MutableStateFlow(true)

        OcrQueue.launchIn(this, readingOriginals) { throw IllegalStateException("the database would not open") }.join()

        assertFalse(readingOriginals.value)
    }
}
