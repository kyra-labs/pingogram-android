package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chats.ui.preview.FeedPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A Channel feed card (`docs/DESIGN_SYSTEM.md` §6 "Channel feed"): "New" on an unread post only, its text, reactions,
 * views and media, and "Open in channel" opening it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FeedPostCardTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<String>()

    private fun setCards(vararg posts: FeedPost) =
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                Column { posts.forEach { post -> FeedPostCard(post = post, onOpen = { opened += post.key }) } }
            }
        }

    @Test
    fun `an unread post says New, a read one only its time`() {
        setCards(FeedPreviewData.text, FeedPreviewData.video)

        composeRule.onNodeWithText("New · 9:41 AM").assertIsDisplayed()
        assertEquals(1, composeRule.onAllNodesWithText("9:41 AM").fetchSemanticsNodes().size)
        composeRule.assertAccessible()
    }

    @Test
    fun `a card shows its text, reactions and views, and opens in its channel`() {
        setCards(FeedPreviewData.text)

        composeRule.onNodeWithText(FeedPreviewData.text.text!!.text, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("👍 128", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("3.4K", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Open in channel").performClick()

        assertEquals(listOf(FeedPreviewData.text.key), opened)
    }

    @Test
    fun `media show their details`() {
        setCards(FeedPreviewData.album, FeedPreviewData.video, FeedPreviewData.poll)

        composeRule.onNodeWithText("+3", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("2:31", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Anonymous poll · 1,284 votes", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("64%", useUnmergedTree = true).assertExists()
    }
}
