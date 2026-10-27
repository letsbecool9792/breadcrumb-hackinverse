package com.lbc.breadcrumb.ui.common

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lbc.breadcrumb.ui.theme.BreadcrumbTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The voice note's player on the phone, playing a real recording (8.7 s) from the cache, never the store. */
@RunWith(AndroidJUnit4::class)
class VoicePlayerTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val file = File(app.cacheDir, "voice-player-test.m4a")

    @Before
    fun copy() {
        InstrumentationRegistry.getInstrumentation().context.assets.open("spoken.m4a").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
    }

    @After
    fun remove() {
        file.delete()
    }

    @Test
    fun itSaysHowLongItIsAndPlaysAndPauses() {
        compose.setContent { BreadcrumbTheme(darkTheme = true) { VoicePlayer(file) } }

        compose.waitUntil(5_000) { compose.onAllNodesWithText("0:09").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("0:00").assertExists()

        // While it plays it draws every frame, so the clock is moved by hand: left to itself,
        // the test would wait for the whole recording to end.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Play").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Pause").assertExists()

        // It plays in real time.
        Thread.sleep(1_300)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("0:01").assertExists()

        compose.onNodeWithContentDescription("Pause").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Play").assertExists()
    }
}
