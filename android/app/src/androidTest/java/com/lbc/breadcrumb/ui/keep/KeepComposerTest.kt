package com.lbc.breadcrumb.ui.keep

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.capture.Draft
import com.lbc.breadcrumb.capture.DraftStore
import com.lbc.breadcrumb.ui.theme.BreadcrumbTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the "+" composer (KeepComposer) and button (KeepSomethingButton) on the phone
 * (design §8, §13; workstream capture C9).
 */
@RunWith(AndroidJUnit4::class)
class KeepComposerTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var context: Context
    private lateinit var draftStore: DraftStore
    private lateinit var testScope: CoroutineScope

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_keep_composer_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        draftStore = DraftStore(context, prefs)
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    @After
    fun tearDown() {
        draftStore.clear()
        testScope.cancel()
    }

    private fun show(
        state: KeepState,
        onBringIn: () -> Unit = {},
        onKept: () -> Unit = {},
    ) {
        compose.setContent {
            BreadcrumbTheme(darkTheme = true) {
                Column {
                    KeepComposer(
                        state = state,
                        onBringIn = onBringIn,
                        onKept = onKept,
                    )
                }
            }
        }
    }

    @Test
    fun keepIsDisabledWithNothingToKeep() {
        val state = KeepState(context, testScope, draftStore)
        show(state)

        compose.onNodeWithText("Keep").assertIsNotEnabled()
    }

    @Test
    fun textAloneKeepsOneMemoryAndEmptiesTheDraft() {
        var keptCount = 0
        // A fake Keep: the real one writes the phone's real database (WrittenCaptureTest covers it, and cleans up).
        val kept = mutableListOf<String>()
        val state = KeepState(context, testScope, draftStore) { _, draft -> kept += draft.text; 1 }
        show(state, onKept = { keptCount++ })

        compose.onNode(hasSetTextAction()).performTextInput("Indiranagar brewery burger")
        compose.waitForIdle()

        assertEquals("Indiranagar brewery burger", state.text)
        compose.onNodeWithText("Keep").assertIsEnabled().performClick()
        compose.waitForIdle()

        assertEquals(1, keptCount)
        assertEquals(listOf("Indiranagar brewery burger"), kept)
        assertEquals("", state.text)
        assertTrue(state.attachments.isEmpty())
        assertFalse(state.lastFailed)

        runBlocking {
            val loaded = draftStore.load()
            assertTrue(loaded.isEmpty())
        }
    }

    @Test
    fun failedKeepShowsTheFailureLineAndKeepsTheDraft() {
        var keptCount = 0
        val state = KeepState(
            context = context,
            scope = testScope,
            store = draftStore,
            keepAction = { _, _ -> 0 },
        )
        show(state, onKept = { keptCount++ })

        compose.onNode(hasSetTextAction()).performTextInput("Failing keep attempt")
        compose.waitForIdle()

        compose.onNodeWithText("Keep").performClick()
        compose.waitForIdle()

        assertEquals(0, keptCount)
        assertTrue(state.lastFailed)
        assertEquals("Failing keep attempt", state.text)
        compose.onNodeWithText("couldn't keep that — try again?").assertIsDisplayed()

        runBlocking {
            val loaded = draftStore.load()
            assertEquals("Failing keep attempt", loaded.text)
        }
    }

    @Test
    fun buttonShowsAppropriateDescriptionForDraftWaiting() {
        val hasDraftState = androidx.compose.runtime.mutableStateOf(false)
        var clicked = false
        compose.setContent {
            BreadcrumbTheme(darkTheme = true) {
                KeepSomethingButton(hasDraft = hasDraftState.value, onClick = { clicked = true })
            }
        }
        compose.onNodeWithContentDescription("Keep something").assertIsDisplayed().performClick()
        assertTrue(clicked)

        hasDraftState.value = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Keep something — a draft is waiting").assertIsDisplayed()
    }

    @Test
    fun bringInQuietLineTriggersCallback() {
        var broughtIn = 0
        val state = KeepState(context, testScope, draftStore)
        show(state, onBringIn = { broughtIn++ })

        compose.onNodeWithText("bring in what's already on the phone").performClick()
        compose.waitForIdle()

        assertEquals(1, broughtIn)
    }
}
