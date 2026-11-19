package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.lifecycle.Lifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chats.ui.preview.FeedPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
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

    private fun setFeed(
        state: FeedUiState,
        openPostKey: String? = null,
    ) = composeRule.setContent {
        PingogramTheme(dynamicColor = false) {
            FeedScreen(state = state, onEvent = { events += it }, onBack = { backs++ }, openPostKey = openPostKey)
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

    @Test
    fun `a long press opens the post's menu, whose items act on the post`() {
        setFeed(posts(unread = listOf(FeedPreviewData.text)))

        composeRule.onNodeWithText("Kotlin Weekly", useUnmergedTree = true).performTouchInput { longClick() }
        composeRule.onNodeWithText("Copy link").assertIsDisplayed()
        composeRule.onNodeWithText("Mark as read").performClick()

        assertEquals(FeedEvent.Act(FeedPreviewData.text.key, FeedPostAction.MarkAsRead), events.last())
        assertEquals(0, composeRule.onAllNodesWithText("Copy link").fetchSemanticsNodes().size)
    }

    @Test
    fun `screen readers get the menu as the card's custom actions`() {
        setFeed(posts(unread = emptyList(), read = listOf(FeedPreviewData.video)))

        val card = composeRule.onNode(hasText("Cooking Club", substring = true) and hasClickAction())
        val actions = card.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(
            listOf("Open in channel", "Forward", "Copy link", "Bookmark", "Mark channel as unread"),
            actions.map { it.label },
        )
        actions.last().action()
        assertEquals(FeedEvent.Act(FeedPreviewData.video.key, FeedPostAction.MarkChannelUnread), events.last())
    }

    @Test
    fun `a channel's ad follows its newest post, labelled, and its buttons act on it`() {
        val ad = FeedPreviewData.ad
        setFeed(
            posts(unread = listOf(FeedPreviewData.text, FeedPreviewData.photo)).copy(ads = persistentMapOf(1L to ad)),
        )

        val post = composeRule.onNodeWithText("Kotlin Weekly", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val label =
            composeRule.onNodeWithContentDescription(
                "Sponsored message from Trip Planner",
                useUnmergedTree = true,
            )
        val adTop = label.fetchSemanticsNode().boundsInRoot.top
        assertEquals(true, post.top < adTop)
        composeRule.onNodeWithText("Open Trip Planner").performClick()
        composeRule.onNodeWithContentDescription("About this ad").performClick()
        composeRule.onNodeWithContentDescription("Report ad").performClick()

        assertEquals(
            listOf(
                FeedAdEvent.Open(ad.chatId, ad.id),
                FeedAdEvent.ShowInfo(ad.chatId, ad.id),
                FeedAdEvent.ShowInfo(ad.chatId, ad.id),
                FeedAdEvent.Report,
            ),
            events.filterIsInstance<FeedAdEvent>().filterNot { it is FeedAdEvent.Viewed },
        )
        assertEquals(true, events.contains(FeedAdEvent.Viewed(ad.chatId, ad.id)))
    }

    @Test
    fun `the post open beside the feed is selected, the others not`() {
        setFeed(
            posts(unread = listOf(FeedPreviewData.text, FeedPreviewData.photo)),
            openPostKey = FeedPreviewData.photo.key,
        )

        composeRule.onNode(hasText("Astronomy Daily", substring = true) and hasClickAction()).assertIsSelected()
        composeRule.onNode(hasText("Kotlin Weekly", substring = true) and hasClickAction()).assertIsNotSelected()
    }

    @Test
    fun `pulling the list reads the posts on screen again`() {
        setFeed(posts(unread = listOf(FeedPreviewData.text)))

        composeRule.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown(startY = top, endY = bottom) }
        composeRule.waitForIdle()

        assertEquals(
            FeedEvent.Refresh(listOf(FeedPreviewData.text.key), pulled = true),
            events.filterIsInstance<FeedEvent.Refresh>().single(),
        )
    }

    @Test
    fun `coming back into view reads the posts on screen again, not on the first start`() {
        setFeed(posts(unread = listOf(FeedPreviewData.text)))
        assertEquals(0, events.filterIsInstance<FeedEvent.Refresh>().size)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        assertEquals(
            FeedEvent.Refresh(listOf(FeedPreviewData.text.key), pulled = false),
            events.filterIsInstance<FeedEvent.Refresh>().single(),
        )
    }

    private companion object {
        const val HALF_A_SECOND = 500L
        const val A_SECOND = 1_000L
    }
}
