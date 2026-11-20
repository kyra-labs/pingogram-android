package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chats.ui.preview.FeedPreviewData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The feed with a hardware keyboard (`REQUIREMENTS.md` FR-X5): the arrows move the focus between cards, the list
 * keeping the focused one in view; Enter opens its post; Tab reaches the buttons in a card, among them More, which
 * opens the post's menu as the Menu key does; and the focus stays on the card whose channel opened beside the feed.
 */
@OptIn(ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FeedKeyboardTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<FeedEvent>()
    private var openPostKey by mutableStateOf<String?>(null)

    private val feed =
        FeedUiState(
            content =
                FeedContent.Posts(
                    persistentListOf(FeedPreviewData.text, FeedPreviewData.photo, FeedPreviewData.album),
                    persistentListOf(),
                ),
            unreadCount = 3,
            channelCount = 3,
        )

    /** The feed as a keyboard user has it: Compose in keyboard mode, where cards take the focus. */
    private fun setFeed() =
        composeRule.setContent {
            val inputMode = LocalInputModeManager.current
            SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
            PingogramTheme(dynamicColor = false) {
                FeedScreen(state = feed, onEvent = { events += it }, onBack = {}, openPostKey = openPostKey)
            }
        }

    private fun card(channel: String) = composeRule.onNode(hasClickAction() and hasText(channel, substring = true))

    private fun press(key: Key) {
        composeRule.onRoot().performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    @Test
    fun `the arrows move between cards, keeping the focused one in view, and Enter opens its post`() {
        setFeed()
        card("Kotlin Weekly").requestFocus()

        press(Key.DirectionDown)
        card("Astronomy Daily").assertIsFocused()
        press(Key.DirectionDown)
        card("Travel Notes").assertIsFocused().assertIsDisplayed()
        press(Key.DirectionUp)
        card("Astronomy Daily").assertIsFocused().assertIsDisplayed()
        press(Key.Enter)

        assertEquals(listOf(FeedEvent.Open(2, 20)), events.filterIsInstance<FeedEvent.Open>())
    }

    @Test
    fun `Tab reaches the card's buttons, and More opens the post's menu`() {
        setFeed()
        card("Kotlin Weekly").requestFocus()

        press(Key.Tab)
        composeRule.onNode(hasContentDescription("More options") and isFocused()).assertExists()
        press(Key.Enter)

        composeRule.onNode(hasText("Forward")).assertIsDisplayed()
        assertTrue(FeedEvent.ShowReactions(FeedPreviewData.text.key) in events)
    }

    @Test
    fun `the Menu key, or Shift+F10, opens the focused card's menu`() {
        setFeed()
        card("Astronomy Daily").requestFocus()

        composeRule.onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.F10) } }
        composeRule.waitForIdle()

        composeRule.onNode(hasText("Forward")).assertIsDisplayed()
        assertTrue(FeedEvent.ShowReactions(FeedPreviewData.photo.key) in events)
    }

    @Test
    fun `the card whose channel opens beside the feed keeps the focus`() {
        setFeed()
        card("Astronomy Daily").requestFocus()
        press(Key.Enter)

        openPostKey = FeedPreviewData.photo.key
        composeRule.waitForIdle()

        card("Astronomy Daily").assertIsFocused()
        press(Key.DirectionDown)
        card("Travel Notes").assertIsFocused()
    }
}
