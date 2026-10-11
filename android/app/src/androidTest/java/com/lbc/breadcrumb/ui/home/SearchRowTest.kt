package com.lbc.breadcrumb.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lbc.breadcrumb.ui.theme.BreadcrumbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The search field and the "+" beside it, on the phone (design §4, §8). */
@RunWith(AndroidJUnit4::class)
class SearchRowTest {

    @get:Rule
    val compose = createComposeRule()

    private var query by mutableStateOf("")
    private var plusTaps = 0
    private lateinit var focus: FocusManager

    private fun show(hasDraft: Boolean = false) {
        compose.setContent {
            focus = LocalFocusManager.current
            BreadcrumbTheme(darkTheme = true) {
                SearchRow(
                    query = query,
                    onQueryChange = { query = it },
                    searching = query.isNotEmpty(),
                    ranking = false,
                    hasDraft = hasDraft,
                    onPlus = { plusTaps++ },
                )
            }
        }
    }

    @Test
    fun thePlusStepsAsideWhileTheFieldIsInUse() {
        show()
        compose.onNodeWithContentDescription("Keep something").assertIsDisplayed()

        compose.onNode(hasSetTextAction()).performTextInput("qualcomm")
        compose.onNodeWithContentDescription("Keep something").assertDoesNotExist()

        // Emptied but still focused, the field is still in use.
        compose.runOnIdle { query = "" }
        compose.onNodeWithContentDescription("Keep something").assertDoesNotExist()

        compose.runOnIdle { focus.clearFocus() }
        compose.onNodeWithContentDescription("Keep something").assertIsDisplayed()
    }

    @Test
    fun thePlusOpensTheSheet() {
        show()
        compose.onNodeWithContentDescription("Keep something").performClick()
        assertEquals(1, plusTaps)
    }

    @Test
    fun aWaitingDraftShowsOnThePlus() {
        show(hasDraft = true)
        compose.onNodeWithContentDescription("Keep something — a draft is waiting").assertIsDisplayed()
    }
}
