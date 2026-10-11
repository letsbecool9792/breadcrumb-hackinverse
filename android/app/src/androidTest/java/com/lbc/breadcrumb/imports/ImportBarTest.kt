package com.lbc.breadcrumb.imports

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportBarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun importBarShowsRunningLineAndTapDismissesIt() {
        var dismissed = false

        compose.setContent {
            ImportBar(
                progress = ImportProgress.Running(
                    current = 2,
                    total = 5,
                    imported = 1,
                    skipped = 0,
                    currentItem = "photo.jpg",
                ),
                onDismiss = { dismissed = true },
            )
        }

        // The bar shows the line
        compose.onNodeWithText("bringing in · 2 of 5").assertIsDisplayed()

        // A tap forgets / dismisses it
        compose.onNodeWithText("bringing in · 2 of 5").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun importBarShowsDoneLine() {
        var dismissed = false

        compose.setContent {
            ImportBar(
                progress = ImportProgress.Done(
                    total = 3,
                    imported = 2,
                    skipped = 1,
                ),
                onDismiss = { dismissed = true },
            )
        }

        compose.onNodeWithText("brought in 2 · 1 already here").assertIsDisplayed()

        compose.onNodeWithText("brought in 2 · 1 already here").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun importBarShowsScanningLine() {
        var dismissed = false

        compose.setContent {
            ImportBar(
                progress = ImportProgress.Scanning("Scanning folder..."),
                onDismiss = { dismissed = true },
            )
        }

        compose.onNodeWithText("looking…").assertIsDisplayed()
        compose.onNodeWithText("looking…").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun importBarDismissesItselfAfterSixSecondsWhenDone() {
        var dismissed = false

        compose.mainClock.autoAdvance = false
        compose.setContent {
            ImportBar(
                progress = ImportProgress.Done(
                    total = 1,
                    imported = 1,
                    skipped = 0,
                ),
                onDismiss = { dismissed = true },
            )
        }

        compose.onNodeWithText("brought in 1").assertIsDisplayed()
        assertFalse(dismissed)

        // Advance past 6 seconds
        compose.mainClock.advanceTimeBy(6100L)
        assertTrue(dismissed)
    }
}
