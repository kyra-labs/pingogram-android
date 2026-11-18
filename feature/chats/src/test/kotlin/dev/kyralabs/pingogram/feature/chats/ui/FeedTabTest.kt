package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Feed tab in the folder row (`docs/DESIGN_SYSTEM.md` §6 "Channel feed"): named with its unread posts for screen
 * readers, opening the feed, and gone while Appearance → Chat list turned it off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FeedTabTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var opened = 0

    private fun setTabs(shown: Boolean = true) =
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalChatSwipes provides ChatSwipes(feedTab = shown)) {
                    FolderTabs(
                        tabs = ChatListPreviewData.folders,
                        selectedFolderId = null,
                        onSelect = {},
                        feedTab = FeedTab(unreadCount = 8, channelCount = 14),
                        onOpenFeed = { opened++ },
                    )
                }
            }
        }

    @Test
    fun `the Feed tab tells its unread posts and opens the feed`() {
        setTabs()

        composeRule
            .onNodeWithContentDescription("Feed, 8 unread posts from channels")
            .assertHasClickAction()
            .performClick()

        assertEquals(1, opened)
        composeRule.assertAccessible()
    }

    @Test
    fun `turned off in Appearance, there is no Feed tab`() {
        setTabs(shown = false)

        assertEquals(
            0,
            composeRule.onAllNodesWithContentDescription("Feed", substring = true).fetchSemanticsNodes().size,
        )
    }
}
