package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What the chat list sends as the user swipes, long-presses, uses screen reader actions, goes back in selection mode
 * and answers the delete dialog (FR-L4, FR-L8). The screen is stateless: these check its events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatListScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatListEvent>()

    // Swipes

    @Test
    fun `swiping a row from start to end marks it as read`() {
        setScreen(ChatListPreviewData.content)

        row(ADA).performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals(listOf(ChatListEvent.RowAction(ADA_ID, ChatRowAction.MarkAsRead)), events)
    }

    @Test
    fun `swiping a row from end to start archives it`() {
        setScreen(ChatListPreviewData.content)

        row(ADA).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(listOf(ChatListEvent.RowAction(ADA_ID, ChatRowAction.Archive)), events)
    }

    @Test
    fun `right to left, swiping from start to end goes leftwards`() {
        setScreen(ChatListPreviewData.content, LayoutDirection.Rtl)

        row(ADA).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(listOf(ChatListEvent.RowAction(ADA_ID, ChatRowAction.MarkAsRead)), events)
    }

    @Test
    fun `swiping in the archive unarchives`() {
        setScreen(ChatListPreviewData.archived)

        row("Orbital Mechanics Weekly").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(listOf(ChatListEvent.RowAction(ORBITAL_ID, ChatRowAction.Unarchive)), events)
    }

    @Test
    fun `rows do not swipe in selection mode`() {
        setScreen(ChatListPreviewData.selecting)

        row(ADA).performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals(emptyList<ChatListEvent>(), events.filterIsInstance<ChatListEvent.RowAction>())
    }

    // Taps and long presses

    @Test
    fun `a long press starts selection`() {
        setScreen(ChatListPreviewData.content)

        row(ADA).performTouchInput { longClick() }

        assertEquals(listOf(ChatListEvent.ChatLongClicked(ADA_ID)), events)
    }

    @Test
    fun `a tap is sent for the selection to decide`() {
        setScreen(ChatListPreviewData.selecting)

        row(GRACE).assertHasClickAction().performClick()

        assertEquals(listOf(ChatListEvent.ChatClicked(GRACE_ID)), events)
    }

    // Screen readers

    @Test
    fun `screen readers get every action of a row, which fire like swipes`() {
        setScreen(ChatListPreviewData.content)
        val actions = row(ADA).fetchSemanticsNode().config[SemanticsActions.CustomActions]

        assertEquals(
            listOf(
                R.string.chat_list_action_mark_read,
                R.string.chat_list_action_archive,
                R.string.chat_list_action_unpin,
            ).plus(listOf(R.string.chat_list_action_mute, R.string.chat_list_action_delete))
                .map(::string),
            actions.map { it.label },
        )
        actions.last().action()
        assertEquals(listOf(ChatListEvent.RowAction(ADA_ID, ChatRowAction.Delete)), events)
    }

    @Test
    fun `screen readers hear selected rows as checked check boxes, without the swipe actions`() {
        setScreen(ChatListPreviewData.selecting)

        row(ADA)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
        row(GRACE).assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
    }

    @Test
    fun `screen readers hear what a long press does`() {
        setScreen(ChatListPreviewData.content)
        val select = string(R.string.chat_list_select)

        row(ADA).assert(
            SemanticsMatcher("long click labeled $select") {
                it.config.getOrNull(SemanticsActions.OnLongClick)?.label == select
            },
        )
    }

    // Selection mode

    @Test
    fun `back leaves selection mode`() {
        setScreen(ChatListPreviewData.selecting)

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(listOf(ChatListEvent.ClearSelection), events)
    }

    @Test
    fun `back outside selection mode is left to navigation`() {
        setScreen(ChatListPreviewData.content)

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(emptyList<ChatListEvent>(), events)
    }

    @Test
    fun `the close button leaves selection mode`() {
        setScreen(ChatListPreviewData.selecting)

        composeRule.onNodeWithContentDescription(string(R.string.chat_list_clear_selection)).performClick()

        assertEquals(listOf(ChatListEvent.ClearSelection), events)
    }

    @Test
    fun `the title counts the selected chats`() {
        setScreen(ChatListPreviewData.selecting)

        composeRule.onNodeWithText("3 selected").assertExists()
    }

    @Test
    fun `the toolbar's buttons act on the selection`() {
        setScreen(ChatListPreviewData.selecting)

        composeRule.onNodeWithContentDescription(string(R.string.chat_list_action_pin)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.chat_list_action_archive)).performClick()

        assertEquals(
            listOf(
                ChatListEvent.SelectionAction(ChatRowAction.Pin),
                ChatListEvent.SelectionAction(ChatRowAction.Archive),
            ),
            events,
        )
    }

    // The archive row

    @Test
    fun `the archive row opens the archive`() {
        setScreen(ChatListPreviewData.contentWithArchive)

        row(string(R.string.chat_list_archived_chats)).performClick()

        assertEquals(listOf(ChatListEvent.OpenArchive), events)
    }

    @Test
    fun `the archive's back button leaves it`() {
        var backs = 0
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatListScreen(state = ChatListPreviewData.archived, onEvent = ::record, onBack = { backs++ })
            }
        }

        composeRule.onNodeWithContentDescription(string(R.string.chat_list_archive_back)).performClick()

        assertEquals(1, backs)
    }

    // The delete dialog

    @Test
    fun `checking the box deletes the chat for the other user too`() {
        setScreen(ChatListPreviewData.deletePrivateChatDialog)

        composeRule.onNodeWithText(string(R.string.chat_list_delete_for_user, "Grace Hopper")).performClick()
        composeRule.onNodeWithText(string(R.string.chat_list_delete_confirm)).performClick()

        assertEquals(listOf(ChatListEvent.ConfirmDelete(forEveryone = true)), events)
    }

    @Test
    fun `without the box the chat is deleted for the current user`() {
        setScreen(ChatListPreviewData.deletePrivateChatDialog)

        composeRule.onNodeWithText(string(R.string.chat_list_delete_confirm)).performClick()

        assertEquals(listOf(ChatListEvent.ConfirmDelete(forEveryone = false)), events)
    }

    @Test
    fun `the check box toggles as one with its label`() {
        setScreen(ChatListPreviewData.deletePrivateChatDialog)
        val checkBox =
            composeRule.onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox) and hasClickAction(),
            )

        checkBox.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        checkBox.performClick()

        checkBox.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @Test
    fun `leaving a group offers no check box and confirms with leave`() {
        setScreen(ChatListPreviewData.leaveGroupDialog)

        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.chat_list_leave_confirm)).performClick()

        assertEquals(listOf(ChatListEvent.ConfirmDelete(forEveryone = false)), events)
    }

    @Test
    fun `cancel dismisses the dialog`() {
        setScreen(ChatListPreviewData.deletePrivateChatDialog)

        composeRule.onNodeWithText(string(R.string.chat_list_cancel)).performClick()

        assertEquals(listOf(ChatListEvent.DismissDelete), events)
    }

    private fun setScreen(
        state: ChatListUiState,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    ChatListScreen(state = state, onEvent = ::record)
                }
            }
        }
    }

    /** Keeps the events these tests are about: not paging, which the list reports as it lays out. */
    private fun record(event: ChatListEvent) {
        if (event !is ChatListEvent.NearEndChanged) events += event
    }

    /** A row by the start of its description, which names its chat. */
    private fun row(name: String): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription("$name,", substring = true) and hasClickAction())

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)

    private companion object {
        const val ADA = "Ada Lovelace"
        const val ADA_ID = 2L
        const val GRACE = "Grace Hopper"
        const val GRACE_ID = 4L
        const val ORBITAL_ID = 5L
    }
}
