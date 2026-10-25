package dev.kyralabs.pingogram.feature.chat.ui.interactions

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.ReactionLabel
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.InteractionsPreviewData
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Seen by" and "Reactions" in a group message's actions, and their sheet (`docs/DESIGN_SYSTEM.md` §6 "Seen by and
 * reactions"): the rows where the actions have them and not elsewhere, the tabs as tabs, the rows as one item each that
 * opens a profile, the next page near the end, "Try again", and a name and a touch target for every control. The
 * sheet is stateless: these check its events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MessageInteractionsSheetTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val chatEvents = mutableListOf<ChatEvent>()
    private val events = mutableListOf<InteractionsEvent>()

    private fun setScreen(state: ChatUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(state = state, onEvent = { chatEvents += it }, onBack = {})
            }
        }
        composeRule.waitForIdle()
    }

    private fun setSheet(sheet: MessageInteractionsSheet) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                MessageInteractionsContent(sheet, onEvent = { events += it })
            }
        }
        composeRule.waitForIdle()
    }

    private val seenBy get() = composeRule.activity.getString(R.string.message_action_seen_by)
    private val reactions get() = composeRule.activity.getString(R.string.message_action_reactions)

    @Test
    fun `a group message's actions open the viewers and the reactions`() {
        setScreen(InteractionsPreviewData.groupActionsSheet)

        composeRule.onNodeWithText(seenBy).performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(ChatEvent.ShowInteractions(24, reactions = false)),
            chatEvents.filterIsInstance<ChatEvent.ShowInteractions>(),
        )
    }

    @Test
    fun `Reactions opens the reactions`() {
        setScreen(InteractionsPreviewData.groupActionsSheet)

        composeRule.onNodeWithText(reactions).performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(ChatEvent.ShowInteractions(24, reactions = true)),
            chatEvents.filterIsInstance<ChatEvent.ShowInteractions>(),
        )
    }

    @Test
    fun `actions without them have neither row`() {
        setScreen(ChatPreviewData.ownActionsSheet)

        composeRule.onAllNodesWithText(seenBy).assertCountEquals(0)
        composeRule.onAllNodesWithText(reactions).assertCountEquals(0)
    }

    @Test
    fun `the tabs are tabs, and a tap selects one`() {
        setSheet(InteractionsPreviewData.seen)

        val tabs = composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        tabs.assertCountEquals(4)
        composeRule.onNodeWithContentDescription("Seen by 4 people").assertExists()
        composeRule.onNodeWithContentDescription("❤, 4 reactions").performClick()

        assertEquals(
            listOf(InteractionsEvent.SelectTab(InteractionsTab.OneReaction(ReactionLabel.Emoji("❤")))),
            events,
        )
    }

    @Test
    fun `a row is one item that opens the profile`() {
        setSheet(InteractionsPreviewData.seen)

        composeRule.onNodeWithContentDescription("Ada Lovelace, Seen at 9:41 AM").performClick()

        assertEquals(listOf(InteractionsEvent.Open(MessageSender.User(101))), events)
    }

    @Test
    fun `on every reaction, a row says the reaction`() {
        setSheet(InteractionsPreviewData.allReactions)

        composeRule.onNodeWithContentDescription("Grace Hopper, reacted 👍, Reacted at 8:20 AM").performClick()

        assertEquals(listOf(InteractionsEvent.Open(MessageSender.User(102))), events)
    }

    @Test
    fun `the end of the list asks for the next page`() {
        val rows =
            (1..40L).map { id ->
                InteractionRow("u$id", MessageSender.User(id), UiText.Plain("Member $id"), null, id, time = null)
            }
        setSheet(
            InteractionsPreviewData.allReactions.copy(
                content = InteractionsContent.Rows(rows.toImmutableList(), MoreInteractions.Available),
            ),
        )
        assertTrue(InteractionsEvent.LoadMore !in events)

        composeRule.onNodeWithTag(INTERACTIONS_LIST_TAG).performScrollToIndex(rows.lastIndex)
        composeRule.waitForIdle()

        assertTrue(InteractionsEvent.LoadMore in events)
    }

    @Test
    fun `a failed tab tries again`() {
        setSheet(InteractionsPreviewData.error)

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.interactions_retry)).performClick()

        assertEquals(listOf(InteractionsEvent.Retry), events)
    }

    @Test
    fun `a failed next page tries again`() {
        setSheet(InteractionsPreviewData.moreFailed)

        composeRule.onNodeWithTag(INTERACTIONS_LIST_TAG).performScrollToIndex(4)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.interactions_retry)).performClick()

        assertEquals(listOf(InteractionsEvent.Retry), events)
    }

    @Test
    fun `every control has a name and a touch target`() {
        listOf(
            InteractionsPreviewData.seen,
            InteractionsPreviewData.allReactions,
            InteractionsPreviewData.oneReaction,
            InteractionsPreviewData.empty,
            InteractionsPreviewData.error,
        ).forEach { sheet ->
            setSheetOnce(sheet)
            composeRule.assertAccessible()
        }
    }

    /** [setSheet] for a test that shows several sheets in turn: one composition, its sheet replaced. */
    private var shown: MutableState<MessageInteractionsSheet>? = null

    private fun setSheetOnce(sheet: MessageInteractionsSheet) {
        val state = shown
        if (state == null) {
            val created = mutableStateOf(sheet)
            shown = created
            composeRule.setContent {
                PingogramTheme(dynamicColor = false) { MessageInteractionsContent(created.value, onEvent = {}) }
            }
        } else {
            state.value = sheet
        }
        composeRule.waitForIdle()
    }
}
