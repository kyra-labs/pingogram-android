package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * The folder tabs' unread counts (`REQUIREMENTS.md` FR-X10): shown and told to screen readers by default, neither
 * while Chat folders → "Unread counters" is off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FolderTabCountersTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setTabs(counters: Boolean) =
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalChatSwipes provides ChatSwipes(folderCounters = counters)) {
                    FolderTabs(tabs = ChatListPreviewData.folders, selectedFolderId = null, onSelect = {})
                }
            }
        }

    private fun countOf(text: String) =
        composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `by default each tab shows its count and tells it`() {
        setTabs(counters = true)

        composeRule.onNodeWithContentDescription("Work, 2 unread chats").assertExists()
        assertEquals(1, countOf("2"))
        composeRule.assertAccessible()
    }

    @Test
    fun `with counters off the tabs show and tell their names alone`() {
        setTabs(counters = false)

        composeRule.onNodeWithContentDescription("Work").assertExists()
        composeRule.onNodeWithContentDescription("Work, 2 unread chats").assertDoesNotExist()
        assertEquals(0, countOf("2"))
        assertEquals(0, countOf("4"))
        composeRule.assertAccessible()
    }
}
