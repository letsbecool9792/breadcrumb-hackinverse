package com.lbc.breadcrumb.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lbc.breadcrumb.ui.theme.BreadcrumbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The sheet on the phone: what it shows, and each way of closing it.
 */
@RunWith(AndroidJUnit4::class)
class SheetLayerTest {

    @get:Rule
    val compose = createComposeRule()

    private var item by mutableStateOf<String?>("opened")
    private var dismissed = 0

    private fun show() {
        compose.setContent {
            BreadcrumbTheme(darkTheme = true) {
                SheetLayer(item = item, onDismiss = { dismissed++ }) { shown, _ ->
                    Text(shown, Modifier.fillMaxWidth().height(200.dp))
                }
            }
        }
    }

    @Test
    fun theSheetShowsItsItem() {
        show()

        compose.onNodeWithText("opened").assertIsDisplayed()
    }

    @Test
    fun nothingIsDrawnWhileClosed() {
        item = null
        show()

        compose.onNodeWithTag("sheet").assertDoesNotExist()
        compose.onNodeWithTag("sheet-scrim").assertDoesNotExist()
    }

    @Test
    fun aTapOnTheScrimDismisses() {
        show()

        compose.onNodeWithTag("sheet-scrim").performTouchInput { click(Offset(20f, 20f)) }

        assertEquals(1, dismissed)
    }

    @Test
    fun aTapOnTheSheetDoesNotDismiss() {
        show()

        compose.onNodeWithText("opened").performClick()

        assertEquals(0, dismissed)
    }

    @Test
    fun draggingTheSheetFarDownDismisses() {
        show()

        compose.onNodeWithTag("sheet").performTouchInput {
            swipe(center, center + Offset(0f, 900f), durationMillis = 400)
        }

        assertEquals(1, dismissed)
    }

    @Test
    fun aShortSlowDragSpringsBackAndKeepsTheSheet() {
        show()

        compose.onNodeWithTag("sheet").performTouchInput {
            swipe(center, center + Offset(0f, 20f), durationMillis = 800)
        }
        compose.waitForIdle()

        assertEquals(0, dismissed)
        compose.onNodeWithText("opened").assertIsDisplayed()
    }

    @Test
    fun backDismisses() {
        show()

        Espresso.pressBack()
        compose.waitForIdle()

        assertEquals(1, dismissed)
    }

    @Test
    fun aPictureTravelsBetweenItsTileAndTheSheetAndBack() {
        compose.setContent {
            BreadcrumbTheme(darkTheme = true) {
                PictureTravelLayer(openedId = item) {
                    Column {
                        TravellingPicture("m1") { mod ->
                            Box(mod.size(80.dp).background(Color.Gray).testTag("tile-picture"))
                        }
                        SheetLayer(item = item, onDismiss = { dismissed++ }) { shown, visibility ->
                            with(visibility) {
                                SheetPicture(shown) { mod ->
                                    Box(mod.size(200.dp).background(Color.Gray).testTag("sheet-picture"))
                                }
                            }
                        }
                    }
                }
            }
        }
        item = null
        compose.waitForIdle()
        compose.onNodeWithTag("tile-picture").assertIsDisplayed()

        item = "m1"
        compose.waitForIdle()
        compose.onNodeWithTag("sheet-picture").assertIsDisplayed()

        item = null
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("sheet-picture").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("tile-picture").assertIsDisplayed()
    }

    @Test
    fun aPictureOutsideATravelLayerJustStaysPut() {
        compose.setContent {
            BreadcrumbTheme(darkTheme = true) {
                TravellingPicture("m1") { mod ->
                    Box(mod.size(80.dp).background(Color.Gray).testTag("tile-picture"))
                }
            }
        }

        compose.onNodeWithTag("tile-picture").assertIsDisplayed()
    }

    @Test
    fun theSheetLeavesWhenItsItemIsCleared() {
        show()
        compose.onNodeWithText("opened").assertIsDisplayed()

        item = null

        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("sheet").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("sheet").assertDoesNotExist()
    }
}
