package com.lbc.breadcrumb.ui.capture

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.ui.theme.BreadcrumbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Closing the capture sheet by swiping it down, on the phone. The sheet is
 * shown on its own, with a text memory, so no picture has to load.
 */
@RunWith(AndroidJUnit4::class)
class CaptureSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val saved = CaptureState.Saved(
        listOf(Memory(type = MemoryType.TEXT, rawText = "Try the brewery burger in Indiranagar")),
    )

    private var dismissed = 0
    private var keptAgain = 0

    // The note lives with the activity; here, with the test.
    private val note = mutableStateOf("")

    private fun show(state: CaptureState, canSay: Boolean = false) {
        compose.setContent {
            BreadcrumbTheme(darkTheme = true) {
                CaptureSheet(
                    state = state,
                    note = note.value,
                    onNoteChange = { note.value = it },
                    onUndo = {},
                    onDismiss = { dismissed++ },
                    onKeepAgain = { keptAgain++ },
                    onSay = if (canSay) ({ true }) else null,
                )
            }
        }
    }

    @Test
    fun swipingTheSheetDownClosesIt() {
        show(saved)

        compose.onNodeWithText("Saved").performTouchInput {
            swipe(center, center + Offset(0f, 1000f), durationMillis = 300)
        }
        compose.waitForIdle()

        assertEquals(1, dismissed)
    }

    @Test
    fun doneClosesOnlyOnceTheExitHasPlayed() {
        show(saved)
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("Done").performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals("not while the exit plays", 0, dismissed)

        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(1, dismissed)
    }

    @Test
    fun aShortSlowDragLetsTheSheetSpringBack() {
        show(saved)

        compose.onNodeWithText("Saved").performTouchInput {
            swipe(center, center + Offset(0f, 40f), durationMillis = 1000)
        }
        compose.waitForIdle()

        assertEquals(0, dismissed)
        compose.onNodeWithText("Saved").assertExists()
    }

    @Test
    fun theLineUnderSavedSaysTheKindAndWhereFrom() {
        show(
            CaptureState.Saved(
                listOf(Memory(type = MemoryType.TEXT, rawText = "Try the brewery burger", sourceAppLabel = "WhatsApp")),
            ),
        )

        compose.onNodeWithText("text · from whatsapp").assertExists()
    }

    @Test
    fun whatIsKeptAlreadySaysSoAndCanBeKeptAgain() {
        show(CaptureState.KeptAlready(listOf(Memory(type = MemoryType.TEXT, rawText = "Try the brewery burger"))))

        compose.onNodeWithText("Kept already").assertExists()
        compose.onNodeWithText("text · kept just now").assertExists()
        compose.onNodeWithText("add a note").assertExists()
        compose.onNodeWithText("Keep again").performClick()
        assertEquals(1, keptAgain)
    }

    @Test
    fun aNoteOnWhatIsKeptAlreadyIsAnotherNote() {
        show(CaptureState.KeptAlready(listOf(Memory(type = MemoryType.LINK, rawText = "https://example.com", note = "from priya"))))

        compose.onNodeWithText("add another note").assertExists()
    }

    @Test
    fun aShareWithSomeKeptAlreadyIsNotSavedSomeOfMore() {
        show(CaptureState.Saved(saved.memories, total = 2, keptAlready = 1))

        compose.onNodeWithText("Saved").assertExists()
        compose.onNodeWithText("text · 1 kept already").assertExists()
    }

    @Test
    fun savingSomeOfMoreSaysSo() {
        show(CaptureState.Saved(saved.memories, total = 3))

        compose.onNodeWithText("Saved 1 of 3").assertExists()
    }

    @Test
    fun theSheetCannotBeSwipedAwayWhileSaving() {
        show(CaptureState.Saving)

        // In words, where a spinner used to be.
        compose.onNodeWithText("Saving…").performTouchInput {
            swipe(center, center + Offset(0f, 1000f), durationMillis = 300)
        }
        compose.waitForIdle()

        assertEquals(0, dismissed)
    }

    @Test
    fun aNoteTypedBeforeTheSwipeIsKept() {
        show(saved)

        compose.onNodeWithText("add a note").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Priya's pick")
        compose.onNodeWithText("Saved").performTouchInput {
            swipe(center, center + Offset(0f, 1000f), durationMillis = 300)
        }
        compose.waitForIdle()

        assertEquals(1, dismissed)
        assertEquals("Priya's pick", note.value)
    }

    @Test
    fun aNoteCanBeSaidOverWhatWasSaved() {
        show(saved, canSay = true)
        compose.onNodeWithText("add a note").assertExists()
        compose.onNodeWithText("say it").assertExists()
    }

    @Test
    fun withNoWayToSayItThereIsNoSayIt() {
        show(saved)
        compose.onNodeWithText("say it").assertDoesNotExist()
    }
}
