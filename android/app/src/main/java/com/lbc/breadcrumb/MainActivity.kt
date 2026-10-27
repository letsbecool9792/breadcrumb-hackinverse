package com.lbc.breadcrumb

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import com.lbc.breadcrumb.capture.ACTION_KEEP
import com.lbc.breadcrumb.capture.ACTION_VOICE
import com.lbc.breadcrumb.ui.debug.MemoryListScreen
import com.lbc.breadcrumb.ui.home.HomeScreen
import com.lbc.breadcrumb.ui.theme.BreadcrumbTheme

class MainActivity : ComponentActivity() {

    /** The debug list, in place of the home screen; held here so a keep request can close it. */
    private var debugOpen by mutableStateOf(false)

    /**
     * How many times the launcher shortcut has asked to keep something (design §13).
     * The home screen opens the "+" sheet for each new one; saved, so a rotation
     * asks nothing twice.
     */
    private var keepRequests by mutableIntStateOf(0)

    /** The same for the "Say something" shortcut, which opens the sheet on the voice panel. */
    private var voiceRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        // must run before super.onCreate() so the splash theme is swapped out
        installSplashScreen()
        super.onCreate(savedInstanceState)
        debugOpen = savedInstanceState?.getBoolean(DEBUG_OPEN) ?: false
        keepRequests = savedInstanceState?.getInt(KEEP_REQUESTS) ?: 0
        voiceRequests = savedInstanceState?.getInt(VOICE_REQUESTS) ?: 0
        // A restored activity's intent was counted the first time round.
        if (savedInstanceState == null) countKeepRequest(intent)
        // Light system-bar icons whatever the phone's theme, since the ground is always ink.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            // Dark always: the app is mostly a frame around other people's
            // screenshots, and a loud ground competes with its own content.
            BreadcrumbTheme(darkTheme = true) {
                // The home screen's saved state outlives a visit to the debug list,
                // so a keep request it already answered is not answered again.
                val saved = rememberSaveableStateHolder()
                // The debug list sits behind a long press on the wordmark, in
                // debug builds only: release passes no callback, so the
                // wordmark has no gesture at all.
                if (debugOpen) {
                    BackHandler { debugOpen = false }
                    MemoryListScreen()
                } else {
                    saved.SaveableStateProvider("home") {
                        HomeScreen(
                            onOpenDebug = if (BuildConfig.DEBUG) ({ debugOpen = true }) else null,
                            keepRequests = keepRequests,
                            voiceRequests = voiceRequests,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        countKeepRequest(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(DEBUG_OPEN, debugOpen)
        outState.putInt(KEEP_REQUESTS, keepRequests)
        outState.putInt(VOICE_REQUESTS, voiceRequests)
    }

    private fun countKeepRequest(intent: Intent?) {
        when (intent?.action) {
            ACTION_KEEP -> keepRequests++
            ACTION_VOICE -> voiceRequests++
            else -> return
        }
        // The debug list gives way, so the sheet opens where it is seen.
        debugOpen = false
    }

    private companion object {
        const val DEBUG_OPEN = "debugOpen"
        const val KEEP_REQUESTS = "keepRequests"
        const val VOICE_REQUESTS = "voiceRequests"
    }
}
