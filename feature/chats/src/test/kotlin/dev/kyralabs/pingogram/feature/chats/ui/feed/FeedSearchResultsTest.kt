package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chats.ui.preview.FeedSearchPreviewStates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the Channel feed's search shows (`REQUIREMENTS.md` FR-X5): what it searches before a query, the posts found as
 * global search's rows that open in their channel, the next page asked for, a failed page's retry, and no results.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FeedSearchResultsTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<FeedSearchEvent>()
    private val opened = mutableListOf<Pair<Long, Long>>()

    private fun setResults(state: FeedSearchUiState) =
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                FeedSearchResults(
                    state = state,
                    onEvent = { events += it },
                    onOpen = { chatId, id -> opened += chatId to id },
                    contentPadding = PaddingValues(),
                )
            }
        }

    @Test
    fun `before a query, it says what it searches`() {
        setResults(FeedSearchUiState())

        composeRule.onNodeWithText("Search your channels").assertIsDisplayed()
        composeRule.onNodeWithText("Find posts in the channels in your feed.").assertIsDisplayed()
    }

    @Test
    fun `a post found opens in its channel, and the end asks for the next page`() {
        setResults(FeedSearchPreviewStates().values.first { it.results.isNotEmpty() && it.canLoadMore })

        composeRule
            .onAllNodes(hasClickAction() and hasContentDescription("Android Weekly", substring = true))
            .onFirst()
            .performClick()

        assertEquals(listOf(-100_2L to 21L), opened)
        assertTrue(FeedSearchEvent.LoadMore in events)
        composeRule.assertAccessible()
    }

    @Test
    fun `a failed page offers Try again`() {
        setResults(FeedSearchUiState("release", error = DomainError.Network))

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf(FeedSearchEvent.Retry), events)
    }

    @Test
    fun `a query that finds nothing says so`() {
        setResults(FeedSearchUiState("relase"))

        composeRule.onNodeWithText("Check the spelling, or try other words.").assertIsDisplayed()
    }
}
