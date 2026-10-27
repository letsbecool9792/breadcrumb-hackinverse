package com.lbc.breadcrumb.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.SyncState
import com.lbc.breadcrumb.open.OriginalAction
import com.lbc.breadcrumb.ui.theme.BreadcrumbTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The detail of a memory, and the undo bar, on the phone (design §11, §12). Shown
 * in a sheet on their own, with memories that carry no files.
 */
@RunWith(AndroidJUnit4::class)
class DetailTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = 10 * 86_400_000L

    private val link = Memory(
        id = "link",
        type = MemoryType.LINK,
        rawText = "https://www.example.com/jobs/4120",
        title = "Software Engineering Intern",
        summary = "A job posting at Qualcomm",
        extractedText = "Apply by April 30",
        sourceAppLabel = "WhatsApp",
        capturedAt = now - 2 * 86_400_000L,
        syncState = SyncState.SYNCED,
    )
    private val note = Memory(
        id = "note",
        type = MemoryType.TEXT,
        rawText = "Try the brewery burger in Indiranagar",
        capturedAt = now,
        syncState = SyncState.PENDING,
    )
    private val picture = Memory(
        id = "picture",
        type = MemoryType.IMAGE,
        title = "Offer letter",
        capturedAt = now,
        extractedText = "Congratulations",
        syncState = SyncState.SYNCED,
    )

    private var mains = 0
    private var deletes = 0
    private var shares = 0
    private var written: List<String?> = emptyList()

    private fun show(memory: Memory, main: OriginalAction, canShare: Boolean = true) {
        val actions = DetailActions(
            main = main,
            onMain = { mains++ },
            onShare = if (canShare) ({ shares++ }) else null,
            onDelete = { deletes++ },
            onNote = { written = written + it },
        )
        compose.setContent {
            BreadcrumbTheme(darkTheme = true) {
                SheetLayer(item = memory, onDismiss = {}, modifier = Modifier.fillMaxSize()) { shown, visibility ->
                    MemoryDetail(shown, now, visibility, actions)
                }
            }
        }
    }

    @Test
    fun aLinkShowsItsHostTitleAddressAndTheWordsAroundIt() {
        show(link, OriginalAction.ViewUrl("https://www.example.com/jobs/4120"))

        compose.onNodeWithText("example.com").assertExists()
        compose.onNodeWithText("Software Engineering Intern").assertExists()
        compose.onNodeWithText("https://www.example.com/jobs/4120").assertExists()
        compose.onNodeWithText("A job posting at Qualcomm").assertExists()
        // Where it came from, and what the phone read from the page.
        compose.onNodeWithText("whatsapp").assertExists()
        compose.onNodeWithText("FROM THE PAGE").assertExists()
        compose.onNodeWithText("Apply by April 30").assertExists()
    }

    @Test
    fun theMainActionNamesWhatItDoesAndDoesIt() {
        show(link, OriginalAction.ViewUrl("https://www.example.com/jobs/4120"))

        compose.onNodeWithText("Open link").performClick()

        assertEquals(1, mains)
    }

    @Test
    fun aNoteCopiesItsTextAndANoteWithoutAFileSaysSo() {
        show(note, OriginalAction.CopyText("Try the brewery burger in Indiranagar"))
        compose.onNodeWithText("Copy text").assertExists()
    }

    @Test
    fun aPictureWhoseFileIsGoneHasNothingToOpen() {
        show(picture, OriginalAction.Missing)

        compose.onNodeWithText("Original isn't on this phone").assertExists()
        compose.onNodeWithText("Original isn't on this phone").performClick()
        assertEquals(0, mains)
        // Its title is the heading, in the serif.
        compose.onNodeWithText("Offer letter").assertExists()
        compose.onNodeWithText("TEXT FOUND").assertExists()
    }

    @Test
    fun shareAndDeleteAreRoundButtons_AndShareIsAbsentWithNothingToShare() {
        show(note, OriginalAction.CopyText("x"), canShare = true)
        compose.onNodeWithContentDescription("Share").performClick()
        compose.onNodeWithContentDescription("Delete").performClick()

        assertEquals(1, shares)
        assertEquals(1, deletes)
    }

    @Test
    fun withNothingToShareThereIsNoShareButton() {
        show(picture, OriginalAction.Missing, canShare = false)

        compose.onNodeWithContentDescription("Share").assertDoesNotExist()
        compose.onNodeWithContentDescription("Delete").assertExists()
    }

    @Test
    fun aNoteIsAddedFromTheDetailAndWrittenOnDone() {
        show(note, OriginalAction.CopyText("x"))

        compose.onNodeWithText("add a note").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("from Priya")
        compose.onNodeWithText("Done").performClick()

        assertEquals(listOf<String?>("from Priya"), written)
    }

    @Test
    fun aWrittenNoteIsShownAndEditedAndBlankClearsIt() {
        show(note.copy(note = "from Priya"), OriginalAction.CopyText("x"))

        compose.onNodeWithText("from Priya").assertExists()
        compose.onNodeWithText("edit").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("")
        compose.onNodeWithText("Done").performClick()

        assertEquals(listOf<String?>(null), written)
    }

    @Test
    fun theSameNoteChangesNothing() {
        show(note.copy(note = "from Priya"), OriginalAction.CopyText("x"))

        compose.onNodeWithText("edit").performClick()
        compose.onNodeWithText("Done").performClick()

        assertEquals(emptyList<String?>(), written)
    }

    @Test
    fun theUndoBarSaysWhatWasDeletedAndBringsItBack() {
        var undone = 0
        var line by mutableStateOf<String?>(undoLine("Software Engineering Intern"))
        compose.setContent {
            BreadcrumbTheme(darkTheme = true) {
                UndoBar(what = line, onUndo = { undone++ })
            }
        }

        compose.onNodeWithText("deleted · software engineering intern").assertExists()
        compose.onNodeWithText("Undo").performClick()
        assertEquals(1, undone)

        line = null
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isEmpty()
        }
        assertNull(line)
    }

    @Test
    fun aVoiceNoteShowsWhatWasSaidOnceAndIsListenedToUntilThen() {
        val heard = Memory(id = "voice", type = MemoryType.AUDIO, extractedText = "The ramen place is closed on Mondays", capturedAt = now)
        var shown by mutableStateOf(heard.copy(extractedText = null, localUri = "file:///gone.m4a"))
        val actions = DetailActions(OriginalAction.Missing, {}, null, {}, {})
        compose.setContent {
            BreadcrumbTheme(darkTheme = true) {
                SheetLayer(item = shown, onDismiss = {}, modifier = Modifier.fillMaxSize()) { memory, visibility ->
                    MemoryDetail(memory, now, visibility, actions)
                }
            }
        }

        compose.onNodeWithText("listening…").assertExists()
        compose.onNodeWithText("still listening to it").assertExists()

        shown = heard
        compose.onNodeWithText("The ramen place is closed on Mondays").assertExists()
        // Said once, in the card: not again as text found.
        compose.onNodeWithText("TEXT FOUND").assertDoesNotExist()
    }

    @Test
    fun aNoteCanBeSaidAsWellAsWritten() {
        show(picture, OriginalAction.Missing)

        compose.onNodeWithText("add a note").assertExists()
        compose.onNodeWithText("say it").assertExists()
    }
}
