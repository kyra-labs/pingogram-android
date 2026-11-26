package dev.kyralabs.pingogram.feature.chats.ui.unanswered

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chats.ui.preview.UnansweredPreviewStates
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Unanswered page (`docs/DESIGN_SYSTEM.md` §6 "Unanswered"): Reply and Dismiss on a row, and the empty state. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class UnansweredScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<UnansweredEvent>()

    private fun setPage(state: UnansweredUiState) =
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                UnansweredScreen(state = state, onEvent = { events += it }, onBack = {})
            }
        }

    @Test
    fun `a row replies, and its close button dismisses it`() {
        val content = UnansweredPreviewStates().values.first() as UnansweredUiState.Content
        val first = content.chats.first()
        setPage(content)
        composeRule.assertAccessible()

        composeRule.onNodeWithContentDescription("Are we still on for Thursday?", substring = true).performClick()
        composeRule.onNodeWithContentDescription("Dismiss Charles Babbage").performClick()

        assertEquals(
            listOf(UnansweredEvent.Reply(first.chatId), UnansweredEvent.Dismiss(first.chatId, first.messageId)),
            events,
        )
    }

    @Test
    fun `with no one waiting the page says so`() {
        setPage(UnansweredUiState.Empty)

        composeRule.onNodeWithText("All caught up").assertIsDisplayed()
    }
}
