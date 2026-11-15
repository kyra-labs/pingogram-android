package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Bookmarks page's controls: the filters, a card's open and remove, and their names for screen readers. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BookmarksScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<BookmarksEvent>()

    private fun show(state: BookmarksUiState = BookmarksPreviewData.content) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { BookmarksScreen(state, onEvent = { events += it }, onBack = {}) }
        }
    }

    @Test
    fun `the selected filter is selected and another selects its tag`() {
        show()

        composeRule.onNodeWithText("All 3").assertIsSelected()
        composeRule.onNodeWithText("Trips 2").performClick()

        assertEquals(listOf<BookmarksEvent>(BookmarksEvent.SelectTag(1)), events)
    }

    @Test
    fun `a card opens its message and its button removes it`() {
        show()

        composeRule.onNodeWithText("Goa trip").performClick()
        composeRule.onAllNodesWithContentDescription("Remove bookmark")[0].performClick()

        assertEquals(
            listOf(BookmarksEvent.Open(chatId = 1, messageId = 10), BookmarksEvent.Remove(chatId = 1, messageId = 10)),
            events,
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `screen readers remove a bookmark through the card's custom action`() {
        show()

        composeRule.onNodeWithText("Goa trip").performCustomAccessibilityActionWithLabel("Remove bookmark")

        assertEquals(listOf<BookmarksEvent>(BookmarksEvent.Remove(chatId = 1, messageId = 10)), events)
    }

    @Test
    fun `the page is accessible`() {
        show()
        composeRule.assertAccessible()
    }

    @Test
    fun `the empty state is accessible`() {
        show(BookmarksUiState.Empty)
        composeRule.onNodeWithText("Nothing bookmarked yet").assertExists()
        composeRule.assertAccessible()
    }
}
