package com.lbc.breadcrumb.voice

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The phone's own recognizer hears a voice note from its file, offline: a recording's AAC and
 * WhatsApp's Opus. The fixtures say "The ramen place near the station is closed on Mondays.
 * Priya says the internship deadline is the thirtieth of April." With an activity on screen,
 * the case the app relies on: off screen it heard them too, under instrumentation, but Android
 * may keep the recognizer from an app in the background.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceTranscriberTest {

    private val app = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun aRecordingIsHeard() = hears("spoken.m4a")

    @Test
    fun aWhatsAppVoiceNoteIsHeard() = hears("spoken.opus")

    private fun hears(asset: String) = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val file = File(app.cacheDir, "transcriber-test-$asset")
        InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        try {
            val heard = ActivityScenario.launch(ComponentActivity::class.java).use {
                OnDeviceTranscriber(app).transcribe(file)
            }
            Log.i("OnDeviceTranscriberTest", "$asset: $heard")
            val words = (heard as? Heard.Words)?.text?.lowercase().orEmpty()
            assertTrue("heard $heard", "ramen" in words && "internship" in words)
        } finally {
            file.delete()
        }
    }
}
