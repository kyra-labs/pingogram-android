package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
 * The Channel feed (`docs/DESIGN_SYSTEM.md` §6 "Channel feed"): its summary, new posts above the read ones, an unread
 * post reported seen after a second on screen, and "No new posts".
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FeedScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<FeedEvent>()

    private fun setFeed(state: FeedUiState) =
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                FeedScreen(state = state, onEvent = { events += it }, onBack = {})
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
    fun `new posts come above the read ones`() {
        setFeed(posts(unread = listOf(FeedPreviewData.text), read = listOf(FeedPreviewData.video)))

        composeRule.onNodeWithText("1 unread · 14 channels").assertIsDisplayed()
        val unread =
            composeRule
                .onNodeWithText(
                    "Kotlin Weekly",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        val read = composeRule.onNodeWithText("Cooking Club", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(true, unread.top < read.top)
        composeRule.assertAccessible()
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
    fun `with nothing new, it says so`() {
        setFeed(FeedUiState(content = FeedContent.NoNewPosts, channelCount = 14))

        composeRule.onNodeWithText("No new posts").assertIsDisplayed()
    }

    private companion object {
        const val HALF_A_SECOND = 500L
        const val A_SECOND = 1_000L
    }
}
