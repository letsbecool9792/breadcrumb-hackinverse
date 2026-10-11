package com.lbc.breadcrumb

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lbc.breadcrumb.capture.ACTION_KEEP
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The launcher shortcut opens the "+" sheet (design §13). The real app runs here,
 * over the phone's real memories: the test only looks and closes the sheet, and
 * keeps nothing.
 */
@RunWith(AndroidJUnit4::class)
class KeepShortcutTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private fun launch(action: String) = ActivityScenario.launch<MainActivity>(
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).setAction(action),
    )

    @Test
    fun theShortcutOpensTheComposerAndARotationDoesNotOpenItAgain() {
        launch(ACTION_KEEP).use { scenario ->
            compose.onNodeWithText("Keep something").assertIsDisplayed()

            // A tap on the scrim, above the sheet, closes it.
            compose.onRoot().performTouchInput { click(Offset(centerX, height * 0.1f)) }
            compose.onNodeWithText("Keep something").assertDoesNotExist()

            scenario.recreate()
            compose.onNodeWithText("Keep something").assertDoesNotExist()
        }
    }

    @Test
    fun anOpenSheetSurvivesARotation() {
        launch(ACTION_KEEP).use { scenario ->
            scenario.recreate()
            compose.onNodeWithText("Keep something").assertIsDisplayed()
        }
    }

    @Test
    fun anOrdinaryLaunchOpensNoSheet() {
        launch(Intent.ACTION_MAIN).use {
            compose.onNodeWithText("Keep something").assertDoesNotExist()
        }
    }
}
