package com.lbc.breadcrumb.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lbc.breadcrumb.embed.ModelStatus
import com.lbc.breadcrumb.ui.theme.BreadcrumbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The model's first-run screen, and its bar, on the phone, with the status given (local-first L8). */
@RunWith(AndroidJUnit4::class)
class FirstRunTest {

    @get:Rule
    val compose = createComposeRule()

    private var mobileData = 0
    private var continued = 0

    @Test
    fun itSaysHowFarAndOffersMobileDataWhileItWaitsForWifi() {
        var status by mutableStateOf<ModelStatus>(ModelStatus.Waiting(142_000_000, ModelStatus.Why.WIFI))
        compose.setContent {
            BreadcrumbTheme(darkTheme = true) {
                FirstRunScreen(status, firstTime = true, onMobileData = { mobileData++ }, onContinue = { continued++ })
            }
        }

        compose.onNodeWithText("Getting search ready").assertExists()
        compose.onNodeWithText("142 of 388 MB").assertExists()
        compose.onNodeWithText("waiting for wi-fi").assertExists()
        compose.onNodeWithText("download it on mobile data now").performClick()
        assertEquals(1, mobileData)
        // Asked once, it is not offered again.
        compose.onNodeWithText("download it on mobile data now").assertDoesNotExist()

        status = ModelStatus.Downloading(200_000_000)
        compose.onNodeWithText("downloading").assertExists()
        compose.onNodeWithText("200 of 388 MB").assertExists()

        status = ModelStatus.Ready
        compose.onNodeWithText("Search is ready").assertExists()
        compose.onNodeWithText("Start keeping").performClick()
        assertEquals(1, continued)
    }

    @Test
    fun openedAgainItIsDone() {
        compose.setContent {
            BreadcrumbTheme(darkTheme = true) {
                FirstRunScreen(ModelStatus.Downloading(1), firstTime = false, onMobileData = {}, onContinue = { continued++ })
            }
        }

        compose.onNodeWithText("download it on mobile data now").assertDoesNotExist()
        compose.onNodeWithText("Done").performClick()
        assertEquals(1, continued)
    }

    @Test
    fun theBarSaysItUntilTheModelIsReadyAndOpensTheScreen() {
        var status by mutableStateOf<ModelStatus>(ModelStatus.Downloading(142_000_000))
        var opened = 0
        compose.setContent {
            BreadcrumbTheme(darkTheme = true) { ModelBar(status, onOpen = { opened++ }) }
        }

        compose.onNodeWithText("getting search ready · 36%").performClick()
        assertEquals(1, opened)

        status = ModelStatus.Ready
        compose.onNodeWithText("getting search ready · 36%").assertDoesNotExist()
    }
}
