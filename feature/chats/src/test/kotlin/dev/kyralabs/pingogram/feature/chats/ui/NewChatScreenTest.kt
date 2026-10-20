package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.NewChatPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What the New chat screen sends as the user taps a contact, an action, "Try again" and back (FR-L7). The screen is
 * stateless: these check its events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class NewChatScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<NewChatEvent>()
    private var backs = 0

    @Test
    fun `a contact opens its chat`() {
        setScreen(NewChatPreviewData.content)

        composeRule.onNode(hasContentDescription("Grace Hopper,", substring = true) and hasClickAction()).performClick()

        assertEquals(listOf(NewChatEvent.OpenChat(4)), events)
    }

    @Test
    fun `new group, channel and contact send their actions`() {
        setScreen(NewChatPreviewData.content)

        composeRule.onNodeWithText(string(R.string.new_chat_group)).assertHasClickAction().performClick()
        composeRule.onNodeWithText(string(R.string.new_chat_channel)).performClick()
        composeRule.onNodeWithText(string(R.string.new_chat_contact)).performClick()

        assertEquals(NewChatAction.entries.map { NewChatEvent.Action(it) }, events)
    }

    @Test
    fun `the actions are there while the contacts load`() {
        setScreen(NewChatPreviewData.loading)

        composeRule.onNodeWithText(string(R.string.new_chat_group)).performClick()

        assertEquals(listOf(NewChatEvent.Action(NewChatAction.Group)), events)
    }

    @Test
    fun `without contacts the screen says so`() {
        setScreen(NewChatPreviewData.empty)

        composeRule.onNodeWithText(string(R.string.new_chat_no_contacts)).assertExists()
    }

    @Test
    fun `try again retries after a failure`() {
        setScreen(NewChatPreviewData.error)

        composeRule.onNodeWithText(string(R.string.search_retry)).performClick()

        assertEquals(listOf(NewChatEvent.Retry), events)
    }

    @Test
    fun `back leaves the screen`() {
        setScreen(NewChatPreviewData.content)

        composeRule.onNodeWithContentDescription(string(R.string.new_chat_back)).performClick()

        assertEquals(1, backs)
    }

    private fun setScreen(state: NewChatUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                NewChatScreen(state = state, onEvent = { events += it }, onBack = { backs++ })
            }
        }
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)
}
