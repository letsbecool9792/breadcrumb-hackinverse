package com.lbc.breadcrumb.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasScrollAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.SyncState
import com.lbc.breadcrumb.ui.theme.BreadcrumbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The home mosaic on the phone: what each kind of tile says, the empty state,
 * and the masthead handing over to the slim strip. Shown on its own, with
 * memories that carry no files, so no picture has to load.
 */
@RunWith(AndroidJUnit4::class)
class MosaicTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = 10 * 86_400_000L

    private val note = Memory(id = "note", type = MemoryType.TEXT, rawText = "Try the brewery burger in Indiranagar", capturedAt = now - 3_000, sourceAppLabel = "WhatsApp", syncState = SyncState.SYNCED)
    private val link = Memory(id = "link", type = MemoryType.LINK, rawText = "https://www.example.com/jobs/4120", title = "Software Engineering Intern", capturedAt = now - 2_000, syncState = SyncState.SYNCED)
    private val pdf = Memory(id = "pdf", type = MemoryType.PDF, title = "Offer letter", capturedAt = now - 1_000, syncState = SyncState.SYNCED)

    private var opened: Memory? = null
    private var longPresses = 0

    private fun show(memories: List<Memory>, kept: String = "${memories.size} kept", withLongPress: Boolean = true) {
        compose.setContent {
            BreadcrumbTheme(darkTheme = true) {
                val entrances = remember { MosaicEntrances() }
                remember(memories) { entrances.submit(memories.map { it.id }) }
                Mosaic(
                    memories = memories,
                    kept = kept,
                    now = now,
                    entrances = entrances,
                    state = rememberLazyStaggeredGridState(),
                    onOpen = { opened = it },
                    onLongPressWordmark = if (withLongPress) ({ longPresses++ }) else null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    @Test
    fun theMastheadSaysHowMuchIsKept() {
        show(listOf(note, link, pdf))

        compose.onNodeWithText("breadcrumb").assertIsDisplayed()
        compose.onNodeWithText("3 KEPT").assertIsDisplayed()
    }

    @Test
    fun eachKindOfTileSaysWhatItIs() {
        show(listOf(note, link, pdf))

        // A note is its own words, a link its host and title, a document its title.
        compose.onNodeWithText("Try the brewery burger in Indiranagar").assertIsDisplayed()
        compose.onNodeWithText("example.com").assertIsDisplayed()
        compose.onNodeWithText("Software Engineering Intern").assertIsDisplayed()
        compose.onNodeWithText("Offer letter").assertIsDisplayed()
        // And where it came from, and how long ago.
        compose.onNodeWithText("now · whatsapp").assertIsDisplayed()
    }

    @Test
    fun tappingATileOpensIt() {
        show(listOf(note, link, pdf))

        compose.onNodeWithText("Software Engineering Intern").performClick()

        assertEquals("link", opened?.id)
    }

    @Test
    fun anEmptyMosaicSaysWhatToDo() {
        show(emptyList(), kept = "0 kept")

        compose.onNodeWithText("Nothing kept yet").assertIsDisplayed()
        compose.onNodeWithText("0 KEPT").assertIsDisplayed()
        compose.onNodeWithText("Start here: the + brings in the screenshots and PDFs already on your phone.").assertIsDisplayed()
    }

    @Test
    fun aLongPressOnTheWordmarkOpensTheDebugList() {
        show(listOf(note))

        compose.onNodeWithText("breadcrumb").performTouchInput { longClick() }

        assertEquals(1, longPresses)
    }

    @Test
    fun withoutACallbackTheWordmarkDoesNothing() {
        show(listOf(note), withLongPress = false)

        compose.onNodeWithText("breadcrumb").performTouchInput { longClick() }

        assertEquals(0, longPresses)
    }

    @Test
    fun scrolledPastTheMastheadASlimStripTakesItsPlaceAndGoesBackToTheTop() {
        val many = (1..40).map {
            Memory(id = "n$it", type = MemoryType.TEXT, rawText = "A note, number $it, with some words in it", capturedAt = now - it * 1_000L, syncState = SyncState.SYNCED)
        }
        show(many)
        // At the top there is the masthead's wordmark alone.
        assertEquals(1, compose.onAllNodesWithText("breadcrumb").fetchSemanticsNodes().size)

        compose.onNode(hasScrollAction()).performScrollToIndex(20)
        compose.waitForIdle()

        // The masthead is gone; its wordmark is now the slim strip's.
        compose.onNodeWithText("breadcrumb").assertIsDisplayed()
        compose.onNodeWithText("breadcrumb").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("40 KEPT").assertIsDisplayed()
    }
}
