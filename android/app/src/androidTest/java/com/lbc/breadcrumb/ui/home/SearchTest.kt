package com.lbc.breadcrumb.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.SyncState
import com.lbc.breadcrumb.embed.Interpretation
import com.lbc.breadcrumb.ui.theme.BreadcrumbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The search field and its results on the phone (design §8, §9), shown on their
 * own with memories that carry no files, so no picture has to load.
 */
@RunWith(AndroidJUnit4::class)
class SearchTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = 10 * 86_400_000L

    private val burger = Memory(
        id = "burger",
        type = MemoryType.TEXT,
        rawText = "Try the brewery burger at that place in Indiranagar",
        capturedAt = now - 2 * 86_400_000L,
        sourceAppLabel = "WhatsApp",
        syncState = SyncState.SYNCED,
    )
    private val job = Memory(
        id = "job",
        type = MemoryType.LINK,
        rawText = "https://www.example.com/jobs/4120",
        title = "Software Engineering Intern",
        summary = "A job posting at Qualcomm",
        capturedAt = now - 3 * 86_400_000L,
        syncState = SyncState.SYNCED,
    )

    private var opened: Memory? = null

    private fun results(
        state: SearchState.Searching,
        total: Int = 17,
    ) {
        compose.setContent {
            BreadcrumbTheme(darkTheme = true) {
                SearchResults(
                    state = state,
                    total = total,
                    now = now,
                    arrivals = SearchArrivals(),
                    onOpen = { opened = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    @Test
    fun theCounterSaysHowTheServerTookThePhrase() {
        results(
            SearchState.Searching(
                phrase = "that burger place",
                results = listOf(SearchResult(burger), SearchResult(job)),
                interpretation = Interpretation(query = "burger", types = listOf("TEXT")),
                status = SearchStatus.RANKED,
            ),
        )

        compose.onNodeWithText("2 of 17 · burger · notes").assertIsDisplayed()
    }

    @Test
    fun aRowSaysItsTitleItsMatchWithTheHitInIt_AndItsAgeAndSource() {
        results(
            SearchState.Searching(
                phrase = "brewery",
                results = listOf(SearchResult(burger)),
                status = SearchStatus.RANKED,
            ),
        )

        // The title, then the words around the hit (the hit is lit within them), then age and source.
        compose.onNodeWithText("Try the brewery burger at that place in Indiranagar").assertIsDisplayed()
        compose.onNodeWithText("Try the brewery burger", substring = true).assertIsDisplayed()
        compose.onNodeWithText("2d · whatsapp · text").assertIsDisplayed()
    }

    @Test
    fun aMatchByMeaningAloneSaysWhatItIs() {
        results(
            SearchState.Searching(
                phrase = "recruiting",
                results = listOf(SearchResult(job)),
                status = SearchStatus.RANKED,
            ),
        )

        compose.onNodeWithText("Software Engineering Intern").assertIsDisplayed()
        compose.onNodeWithText("A job posting at Qualcomm").assertIsDisplayed()
    }

    @Test
    fun tappingARowOpensIt() {
        results(
            SearchState.Searching("burger", listOf(SearchResult(burger), SearchResult(job)), status = SearchStatus.RANKED),
        )

        compose.onNodeWithText("Software Engineering Intern").performClick()

        assertEquals("job", opened?.id)
    }

    @Test
    fun nothingMatchingSaysSo() {
        results(SearchState.Searching("xyzzy", emptyList(), status = SearchStatus.RANKED))

        compose.onNodeWithText("Nothing matches that").assertIsDisplayed()
        compose.onNodeWithText("0 of 17 · xyzzy").assertIsDisplayed()
    }

    @Test
    fun withTheServerOutOfReachItSaysTheWordsAloneWereSearched() {
        results(SearchState.Searching("xyzzy", emptyList(), status = SearchStatus.OFFLINE))

        compose.onNodeWithText("No saved words match").assertIsDisplayed()
        compose.onNodeWithText("0 of 17 · xyzzy · offline · words only").assertIsDisplayed()
    }

    @Test
    fun theFieldShowsItsHintAndWhatIsTyped_AndClears() {
        var query by mutableStateOf("")
        compose.setContent {
            BreadcrumbTheme(darkTheme = true) {
                SearchField(query = query, onQueryChange = { query = it }, searching = false, ranking = false)
            }
        }
        compose.onNodeWithText("search your memory").assertIsDisplayed()

        compose.onNode(hasSetTextAction()).performTextInput("qualcomm")

        assertEquals("qualcomm", query)
        compose.onNodeWithContentDescription("Clear search").performClick()
        assertEquals("", query)
        compose.onNodeWithText("search your memory").assertIsDisplayed()
    }
}
