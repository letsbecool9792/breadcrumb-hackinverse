package com.lbc.breadcrumb

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.lbc.breadcrumb.ui.debug.MemoryListScreen
import com.lbc.breadcrumb.ui.home.HomeScreen
import com.lbc.breadcrumb.ui.theme.BreadcrumbTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // must run before super.onCreate() so the splash theme is swapped out
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Light system-bar icons whatever the phone's theme, since the ground is always ink.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            // Dark always: the app is mostly a frame around other people's
            // screenshots, and a loud ground competes with its own content.
            BreadcrumbTheme(darkTheme = true) {
                // The debug list sits behind a long press on the wordmark, in
                // debug builds only: release passes no callback, so the
                // wordmark has no gesture at all.
                var debugOpen by rememberSaveable { mutableStateOf(false) }
                if (debugOpen) {
                    BackHandler { debugOpen = false }
                    MemoryListScreen()
                } else {
                    HomeScreen(onOpenDebug = if (BuildConfig.DEBUG) ({ debugOpen = true }) else null)
                }
            }
        }
    }
}
