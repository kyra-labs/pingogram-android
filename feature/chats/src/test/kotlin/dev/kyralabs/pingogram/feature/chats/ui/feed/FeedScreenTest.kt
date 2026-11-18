package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chats.ui.preview.FeedPreviewData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Channel feed (`docs/DESIGN_SYSTEM.md` §6 "Channel feed"): its summary, new posts above "Earlier · read", "You're
 * all caught up" once they are read, an unread post reported seen after a second on screen, and "No new posts".
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FeedScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<FeedEvent>()
    private var backs = 0

    private fun setFeed(state: FeedUiState) =
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                FeedScreen(state = state, onEvent = { events += it }, onBack = { backs++ })
            }
        }

    private fun posts(
        unread: List<FeedPost>,
        read: List<FeedPost> = emptyList(),
        readHere: Int = 0,
    ) = FeedUiState(
        content =
            FeedContent.Posts(
                persistentListOf(*unread.toTypedArray()),
                persistentListOf(*read.toTypedArray()),
                readHere,
            ),
        unreadCount = unread.count(FeedPost::isUnread),
        channelCount = 14,
    )

    @Test
    fun `new posts come above Earlier read, then the read ones`() {
        setFeed(posts(unread = listOf(FeedPreviewData.text), read = listOf(FeedPreviewData.video)))

        composeRule.onNodeWithText("1 unread · 14 channels").assertIsDisplayed()
        val divider = composeRule.onNodeWithText("Earlier · read").fetchSemanticsNode().boundsInRoot
        val unread =
            composeRule
                .onNodeWithText(
                    "Kotlin Weekly",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        val read = composeRule.onNodeWithText("Cooking Club", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(true, unread.top < divider.top && divider.top < read.top)
        composeRule.assertAccessible()
    }

    @Test
    fun `once the new posts are read, You're all caught up says how many`() {
        val read = FeedPreviewData.text.copy(isUnread = false)
        setFeed(posts(unread = listOf(read), read = listOf(FeedPreviewData.video), readHere = 1))

        composeRule.onNodeWithText("You're all caught up").assertIsDisplayed()
        composeRule.onNodeWithText("1 new post read · older posts below").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Earlier · read").fetchSemanticsNodes().size)
    }

    @Test
    fun `an unread post on screen for a second is reported seen`() {
        composeRule.mainClock.autoAdvance = false
        setFeed(posts(unread = listOf(FeedPreviewData.text)))
        composeRule.mainClock.advanceTimeBy(HALF_A_SECOND)
        assertEquals(emptyList<FeedEvent>(), events)

        composeRule.mainClock.advanceTimeBy(A_SECOND)

        val post = FeedPreviewData.text
        assertEquals(listOf(FeedEvent.Seen(post.chatId, post.messageIds)), events)
    }

    @Test
    fun `with nothing new, it says so and offers chats and read posts`() {
        setFeed(FeedUiState(content = FeedContent.NoNewPosts, channelCount = 14))

        composeRule.onNodeWithText("No new posts").assertIsDisplayed()
        composeRule.onNodeWithText("Back to chats").performClick()
        composeRule.onNodeWithText("Show read posts").performClick()

        assertEquals(1, backs)
        assertEquals(listOf<FeedEvent>(FeedEvent.ShowReadPosts), events)
        composeRule.assertAccessible()
    }

    @Test
    fun `a failed read shows its error above the saved posts, and Try again retries`() {
        setFeed(posts(unread = listOf(FeedPreviewData.text)).copy(error = DomainError.Network))

        val error = composeRule.onNodeWithText("Couldn't load new posts").fetchSemanticsNode().boundsInRoot
        val post = composeRule.onNodeWithText("Kotlin Weekly", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(true, error.top < post.top)
        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(FeedEvent.Retry, events.last())
        composeRule.assertAccessible()
    }

    @Test
    fun `a failed read with nothing saved shows the error alone`() {
        setFeed(FeedUiState(content = FeedContent.NoNewPosts, error = DomainError.Network))

        composeRule.onNodeWithText("Couldn't load new posts").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("No new posts").fetchSemanticsNodes().size)
    }

    @Test
    fun `while loading, skeleton cards are one Loading posts for screen readers`() {
        setFeed(FeedUiState(content = FeedContent.Loading, channelCount = 14))

        composeRule.onNodeWithContentDescription("Loading posts…").assertExists()
        assertEquals(1, composeRule.onAllNodesWithText("Loading posts…").fetchSemanticsNodes().size)
    }

    private companion object {
        const val HALF_A_SECOND = 500L
        const val A_SECOND = 1_000L
    }
}
