package com.lbc.breadcrumb.embed

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lbc.breadcrumb.BreadcrumbApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * The real model with a picture's vision encoder on the GPU (roadmap 6): the same vector as the
 * CPU's, words untouched by it, and the choice remembered. On a phone whose GPU won't run it, the
 * picture still gets its vector, on the CPU. Skipped when the model isn't on the phone.
 */
@RunWith(AndroidJUnit4::class)
class BackendsOnPhoneTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as BreadcrumbApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = scope.cancel()

    private fun picture(): ByteArray {
        val bitmap = Bitmap.createBitmap(1_000, 1_500, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(200, 120, 40))
            drawCircle(500f, 600f, 300f, Paint().apply { color = Color.WHITE })
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        for (i in a.indices) dot += a[i] * b[i]
        return dot / sqrt(a.sumOf { it.toDouble() * it } * b.sumOf { it.toDouble() * it })
    }

    @Test
    fun aPictureOnTheGpuIsThePictureOnTheCpu() = runBlocking {
        assumeNotNull(app.models.ready())
        val jpeg = picture()
        var stored: String? = null
        val onGpu = Embedder(app.models::ready, context.cacheDir, scope, GpuChoice({ stored }, { stored = it }))
        val onCpu = Embedder(app.models::ready, context.cacheDir, scope)
        try {
            val cpuWords = onCpu.text("task: search result | query: that ramen place")!!
            val cpuPicture = onCpu.picture(jpeg, "title: none | text: ramen")!!
            onCpu.close()

            // Words first (an engine without the GPU), then a picture (it starts again with it).
            val gpuWords = onGpu.text("task: search result | query: that ramen place")!!
            val gpuPicture = onGpu.picture(jpeg, "title: none | text: ramen")!!

            assertArrayEquals(cpuWords, gpuWords, 1e-4f)
            assertTrue("cosine ${cosine(cpuPicture, gpuPicture)}", cosine(cpuPicture, gpuPicture) > 0.99)
            // This phone's GPU ran it, or it failed and the CPU did: either way, remembered.
            assertTrue(stored == "works" || stored == "fails")
        } finally {
            onGpu.close()
            onCpu.close()
        }
    }

    @Test
    fun aPhoneWhoseGpuFailedEmbedsPicturesOnTheCpu() = runBlocking {
        assumeNotNull(app.models.ready())
        var stored: String? = "fails"
        val embedder = Embedder(app.models::ready, context.cacheDir, scope, GpuChoice({ stored }, { stored = it }))
        try {
            assertEquals(768, embedder.picture(picture(), null)!!.size)
            assertEquals("fails", stored)
        } finally {
            embedder.close()
        }
    }
}
