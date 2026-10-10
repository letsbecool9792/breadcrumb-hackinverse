package com.lbc.breadcrumb

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.lbc.breadcrumb.ui.debug.MemoryListScreen
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
                // Until the search screens exist, the debug list is the app.
                // They replace it here; it then moves behind a long press on
                // the wordmark, in debug builds only.
                MemoryListScreen()
            }
        }
    }
}
