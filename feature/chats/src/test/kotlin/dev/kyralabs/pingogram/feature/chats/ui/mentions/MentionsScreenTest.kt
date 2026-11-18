package dev.kyralabs.pingogram.feature.chats.ui.mentions

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chats.ui.preview.SearchPreviewData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Mentions & replies page (`docs/DESIGN_SYSTEM.md` §6): its two groups under their headers, a row opening its
 * message, the empty state, and "Try again" after an error.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MentionsScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<MentionsEvent>()

    private fun setPage(state: MentionsUiState) =
        composeRule.setContent {
            PingogramTheme(
                dynamicColor = false,
            ) { MentionsScreen(state = state, onEvent = { events += it }, onBack = {}) }
        }

    @Test
    fun `unread messages come under their header, then the earlier ones, and a row opens its message`() {
        val (first, second) = SearchPreviewData.messages
        setPage(MentionsUiState.Content(unread = persistentListOf(first), earlier = persistentListOf(second)))

        val unread =
            composeRule
                .onNodeWithText("Unread")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val earlier =
            composeRule
                .onNodeWithText("Earlier")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertEquals(true, unread.top < earlier.top)
        composeRule.onNodeWithText("Charles Babbage", substring = true, useUnmergedTree = true).performClick()

        assertEquals(listOf(MentionsEvent.Open(first.chatId, first.messageId)), events)
    }

    @Test
    fun `without unread messages there is no Unread header`() {
        setPage(MentionsUiState.Content(unread = persistentListOf(), earlier = SearchPreviewData.messages))

        composeRule.onNodeWithText("Earlier").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Unread").fetchSemanticsNodes().size)
    }

    @Test
    fun `nothing found says so`() {
        setPage(MentionsUiState.Empty)

        composeRule.onNodeWithText("No mentions or replies").assertIsDisplayed()
    }

    @Test
    fun `after an error Try again asks for them again`() {
        setPage(MentionsUiState.Error(DomainError.Network))

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf<MentionsEvent>(MentionsEvent.Retry), events)
    }
}
