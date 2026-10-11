package com.lbc.breadcrumb.ui.home

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.capture.Draft
import com.lbc.breadcrumb.capture.DraftStore
import com.lbc.breadcrumb.ui.keep.KeepState
import com.lbc.breadcrumb.ui.theme.BreadcrumbTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "+" sheet's two panels on the phone (design §13, §14). The draft lives in
 * preferences of its own, so the person's real draft is never touched, and Keep
 * is faked, so nothing is saved.
 */
@RunWith(AndroidJUnit4::class)
class PlusSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var context: Context
    private lateinit var store: DraftStore
    private lateinit var scope: CoroutineScope

    private var panel by mutableStateOf(PlusPanel.COMPOSER)
    private var closed = false
    private val kept = mutableListOf<Draft>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("test_plus_sheet_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = DraftStore(context, prefs)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    @After
    fun tearDown() {
        store.clear()
        scope.cancel()
    }

    private fun show(start: PlusPanel) {
        panel = start
        val state = KeepState(context, scope, store) { _, draft -> kept += draft; 1 }
        val importer = (context as BreadcrumbApp).importer
        compose.setContent {
            BreadcrumbTheme(darkTheme = true) {
                Column {
                    PlusSheet(
                        panel = panel,
                        keepState = state,
                        importer = importer,
                        onPanel = { panel = it },
                        onClose = { closed = true },
                    )
                }
            }
        }
    }

    @Test
    fun theQuietLineLeadsToTheImportPanelAndBackLeadsHome() {
        show(PlusPanel.COMPOSER)
        compose.onNodeWithText("Keep something").assertIsDisplayed()

        compose.onNodeWithText("bring in what's already on the phone").performClick()
        compose.onNodeWithText("Bring in").assertIsDisplayed()
        compose.onNodeWithText("A whole folder").assertIsDisplayed()

        compose.onNodeWithText("back").performClick()
        compose.onNodeWithText("Keep something").assertIsDisplayed()
        assertEquals(PlusPanel.COMPOSER, panel)
    }

    @Test
    fun theImportPanelCanBeWhereItOpens() {
        show(PlusPanel.IMPORT)
        compose.onNodeWithText("Bring in").assertIsDisplayed()
    }

    @Test
    fun keepingClosesTheSheet() {
        show(PlusPanel.COMPOSER)
        compose.onNode(hasSetTextAction()).performTextInput("the ramen place near the station")
        compose.onNodeWithText("Keep").performClick()
        compose.waitUntil(5_000) { closed }

        assertTrue(closed)
        assertEquals("the ramen place near the station", kept.single().text)
    }
}
