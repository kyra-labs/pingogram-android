package dev.kyralabs.pingogram.feature.profile.invite

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The join sheet joins only on its button, says what a request does, and closes from "Cancel". */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatInviteSheetTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatInviteEvent>()
    private var cancelled = 0

    private fun setContent(state: ChatInviteUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { ChatInviteContent(state, { events += it }, { cancelled++ }) }
        }
    }

    @Test
    fun `join sends its event, and nothing joins before`() {
        setContent(ChatInvitePreviewData.group)
        assertEquals(emptyList<ChatInviteEvent>(), events)

        composeRule.onNodeWithText("Join group").performClick()

        assertEquals(listOf(ChatInviteEvent.Join), events)
    }

    @Test
    fun `a channel that takes requests asks for one`() {
        setContent(ChatInvitePreviewData.channelRequest)

        composeRule.onNodeWithText("An admin has to accept your request to join this channel.").assertExists()
        composeRule.onNodeWithText("Request to join").performClick()

        assertEquals(listOf(ChatInviteEvent.Join), events)
    }

    @Test
    fun `join waits while Telegram answers`() {
        setContent(ChatInvitePreviewData.group.copy(isJoining = true))

        composeRule.onNodeWithText("Join group").assertIsNotEnabled()
    }

    @Test
    fun `cancel closes the sheet`() {
        setContent(ChatInvitePreviewData.group)

        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(1, cancelled)
    }

    @Test
    fun `an expired link closes, a lost connection tries again`() {
        setContent(ChatInviteUiState.Failed(DomainError.Network, canRetry = true))

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf(ChatInviteEvent.Retry), events)
    }
}
