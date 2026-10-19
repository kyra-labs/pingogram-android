package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.SearchPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What global search sends as the user types, taps results, removes recent chats, retries and leaves, and what screen
 * readers hear: headings, rows as one, and the number of results. The screen is stateless: these check its events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SearchScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<SearchEvent>()
    private var backPressed = false

    private fun setScreen(
        state: SearchUiState,
        textFieldState: TextFieldState = TextFieldState(SearchPreviewData.queryOf(state)),
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                SearchScreen(
                    state = state,
                    textFieldState = textFieldState,
                    onEvent = { events += it },
                    onBack = { backPressed = true },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `typing sends the query`() {
        setScreen(SearchPreviewData.suggestions)

        composeRule.onNode(hasSetTextAction()).performTextInput("ada")
        composeRule.waitForIdle()

        assertEquals(SearchEvent.QueryChanged("ada"), events.last())
    }

    @Test
    fun `the clear button empties the field and the query`() {
        val textFieldState = TextFieldState("ada")
        setScreen(SearchPreviewData.results, textFieldState)

        composeRule.onNodeWithContentDescription(string(R.string.search_clear)).performClick()
        composeRule.waitForIdle()

        assertEquals("", textFieldState.text.toString())
        assertTrue(SearchEvent.ClearQuery in events)
        assertEquals(SearchEvent.QueryChanged(""), events.last())
    }

    @Test
    fun `the back arrow leaves search`() {
        setScreen(SearchPreviewData.suggestions)

        composeRule.onNodeWithContentDescription(string(R.string.search_back)).performClick()

        assertTrue(backPressed)
    }

    @Test
    fun `the sections show under headings, which screen readers hear as such`() {
        setScreen(SearchPreviewData.results)

        listOf(
            R.string.search_section_chats,
            R.string.search_section_contacts,
            R.string.search_section_global,
            R.string.search_section_messages,
        ).forEach { composeRule.onNodeWithText(string(it)).assert(isHeading) }
    }

    @Test
    fun `tapping a chat opens it`() {
        setScreen(SearchPreviewData.results)

        composeRule.onNodeWithContentDescription("Ada Lovelace, Online, @ada").assertHasClickAction().performClick()

        assertEquals(listOf(SearchEvent.OpenChat(2)), events.filterIsInstance<SearchEvent.OpenChat>())
    }

    @Test
    fun `tapping a message opens it, and screen readers hear the row as one`() {
        setScreen(SearchPreviewData.results)

        composeRule
            .onNodeWithContentDescription("Charles Babbage, Lady Ada sent her notes on the Bernoulli numbers, 9:41 AM")
            .performClick()

        assertEquals(
            listOf(SearchEvent.OpenMessage(chatId = 6, messageId = 61)),
            events.filterIsInstance<SearchEvent.OpenMessage>(),
        )
    }

    @Test
    fun `a message in a group is heard with its sender`() {
        setScreen(SearchPreviewData.results)

        composeRule
            .onNodeWithContentDescription(
                "Ada's Book Club, Grace: …next month we read the letters Ada wrote to Babbage about the engine, Mon",
            ).assertHasClickAction()
    }

    @Test
    fun `screen readers hear how many results were found`() {
        setScreen(SearchPreviewData.results)

        composeRule
            .onNodeWithContentDescription("7 results")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun `a recent chat can be removed, and all of them cleared`() {
        setScreen(SearchPreviewData.suggestions)

        composeRule.onNodeWithContentDescription(string(R.string.search_remove_recent, "Ada Lovelace")).performClick()
        composeRule.onNodeWithText(string(R.string.search_clear_recents)).performClick()

        assertEquals(
            listOf(SearchEvent.RemoveRecent(2), SearchEvent.ClearRecents),
            events.filterNot { it is SearchEvent.QueryChanged },
        )
    }

    @Test
    fun `a person in the top row opens the chat with them`() {
        setScreen(SearchPreviewData.suggestions)

        composeRule.onNodeWithContentDescription("Grace Hopper, Online").performClick()

        assertEquals(listOf(SearchEvent.OpenChat(8)), events.filterNot { it is SearchEvent.QueryChanged })
    }

    @Test
    fun `try again after a section failed sends Retry`() {
        setScreen(SearchPreviewData.error)

        composeRule.onNode(hasText(string(R.string.search_retry))).performClick()

        assertTrue(SearchEvent.Retry in events)
    }

    @Test
    fun `a list that shows its end while more messages can load asks for them`() {
        setScreen(SearchPreviewData.results)

        assertTrue(SearchEvent.LoadMoreMessages in events)
    }

    @Test
    fun `a query that found nothing says so`() {
        setScreen(SearchPreviewData.noResults)

        composeRule.onNodeWithText(string(R.string.search_no_results_title, "qwxz")).assert(isHeading)
    }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
}
