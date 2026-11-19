package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chats.ui.preview.FeedPreviewData
import dev.kyralabs.pingogram.feature.chats.ui.preview.FeedSearchPreviewStates
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Channel feed's search in the feed (`REQUIREMENTS.md` FR-X5, owner decision 2026-11-19): the search button opens a
 * field in place of the top bar, a post found opens in its channel as a card does, and back closes the search.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FeedSearchScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<FeedEvent>()
    private val searchEvents = mutableListOf<FeedSearchEvent>()
    private var backs = 0

    private val feed =
        FeedUiState(
            content = FeedContent.Posts(persistentListOf(FeedPreviewData.text), persistentListOf()),
            unreadCount = 1,
            channelCount = 14,
            filter = FeedFilter(chosen = persistentSetOf(-100_1L)),
        )

    private fun setFeed(search: FeedSearchUiState = FeedSearchUiState()) =
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                FeedScreen(
                    state = feed,
                    onEvent = { events += it },
                    onBack = { backs++ },
                    search = search,
                    onSearchEvent = { searchEvents += it },
                )
            }
        }

    private fun openSearch() {
        composeRule.onNodeWithContentDescription("Search posts").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `the search button opens a field that searches what is typed, and back closes it`() {
        setFeed()
        assertTrue(FeedSearchEvent.ChannelsChosen(setOf(-100_1L)) in searchEvents)

        openSearch()
        composeRule.onNodeWithText("Search your channels").assertIsDisplayed()
        composeRule.assertAccessible()
        composeRule.onNode(hasSetTextAction()).performTextInput("release")
        composeRule.waitForIdle()
        assertEquals(FeedSearchEvent.QueryChanged("release"), searchEvents.last())

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(0, backs)
        assertEquals(FeedSearchEvent.QueryChanged(""), searchEvents.last())
        composeRule.onNodeWithContentDescription("Search posts").assertIsDisplayed()
    }

    @Test
    fun `a post found opens in its channel, as a card does`() {
        val found = FeedSearchPreviewStates().values.first { it.results.isNotEmpty() && it.error == null }
        setFeed(found)
        openSearch()

        composeRule
            .onAllNodes(hasClickAction() and hasContentDescription("Android Weekly", substring = true))
            .onFirst()
            .performClick()

        assertEquals(listOf(FeedEvent.Open(-100_2L, 21L)), events)
        composeRule.assertAccessible()
    }
}
