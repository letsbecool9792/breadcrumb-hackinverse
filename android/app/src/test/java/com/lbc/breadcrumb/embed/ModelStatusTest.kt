package com.lbc.breadcrumb.embed

import com.lbc.breadcrumb.embed.ModelStatus.Why
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelStatusTest {

    private fun of(
        ready: Boolean = false,
        progress: ModelStore.Progress = ModelStore.Progress.Idle,
        downloaded: Long = 0,
        online: Boolean = true,
        unmetered: Boolean = true,
    ) = ModelStatus.of(ready, progress, downloaded, online, unmetered)

    @Test
    fun `ready wins, then what the download is doing, then why it waits`() {
        assertEquals(ModelStatus.Ready, of(ready = true, online = false))
        assertEquals(ModelStatus.Downloading(5), of(progress = ModelStore.Progress.Downloading(5), unmetered = false))
        assertEquals(ModelStatus.Checking, of(progress = ModelStore.Progress.Checking))
        assertEquals(ModelStatus.Waiting(7, Why.NO_INTERNET), of(downloaded = 7, online = false))
        assertEquals(ModelStatus.Waiting(7, Why.WIFI), of(downloaded = 7, unmetered = false))
        assertEquals(ModelStatus.Waiting(0, Why.STARTING), of())
    }

    @Test
    fun `the first run says how far, in the phone's megabytes`() {
        val third = ModelStatus.Downloading(142_000_000)
        assertEquals("142 of 388 MB", ModelStatus.amount(third))
        assertEquals("downloading", ModelStatus.state(third))
        assertEquals(0.366f, ModelStatus.fraction(third), 0.001f)
        assertEquals("0 of 388 MB", ModelStatus.amount(ModelStatus.Waiting(0, Why.WIFI)))
        assertEquals("waiting for wi-fi", ModelStatus.state(ModelStatus.Waiting(0, Why.WIFI)))
        assertEquals("388 of 388 MB", ModelStatus.amount(ModelStatus.Ready))
        assertEquals("ready", ModelStatus.state(ModelStatus.Ready))
    }

    @Test
    fun `the bar says it until the model is ready, and never 100 percent before`() {
        assertEquals("getting search ready · 36%", ModelStatus.bar(ModelStatus.Downloading(142_000_000)))
        assertEquals("getting search ready · 99%", ModelStatus.bar(ModelStatus.Downloading(ModelStore.SIZE)))
        assertEquals("search by meaning waits for wi-fi", ModelStatus.bar(ModelStatus.Waiting(0, Why.WIFI)))
        assertEquals("search by meaning waits for the internet", ModelStatus.bar(ModelStatus.Waiting(0, Why.NO_INTERNET)))
        assertEquals("getting search ready · checking it", ModelStatus.bar(ModelStatus.Checking))
        assertNull(ModelStatus.bar(ModelStatus.Ready))
    }
}
