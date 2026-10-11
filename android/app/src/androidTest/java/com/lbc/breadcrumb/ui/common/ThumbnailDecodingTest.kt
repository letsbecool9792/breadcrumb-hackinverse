package com.lbc.breadcrumb.ui.common

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * Decodes are limited: a decode cannot be cancelled once begun, so a fast
 * scroll would otherwise start dozens at once.
 */
@RunWith(AndroidJUnit4::class)
class ThumbnailDecodingTest {

    @Test
    fun tilesWaitTheirTurnInsteadOfDecodingAllAtOnce() = runBlocking {
        val running = AtomicInteger()
        val most = AtomicInteger()

        (1..24).map {
            async {
                withContext(Thumbnails.decoding) {
                    most.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                    Thread.sleep(20)
                    running.decrementAndGet()
                }
            }
        }.awaitAll()

        assertEquals(0, running.get())
        assertTrue("most at once was ${most.get()}", most.get() in 1..3)
    }
}
