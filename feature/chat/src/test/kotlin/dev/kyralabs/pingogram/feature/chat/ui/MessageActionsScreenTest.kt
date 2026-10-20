package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The message actions on the conversation screen (`REQUIREMENTS.md` FR-C6, FR-C7): the long press and its screen reader
 * equivalent, the sheet's actions, quick reactions and reactions picker, the delete confirmation's check box, the reply
 * and edit bars, the reaction badges, and the custom actions every bubble offers screen readers. The screen is
 * stateless: these check its events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MessageActionsScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()

    private fun setScreen(
        state: ChatUiState,
        text: ComposerText = ComposerText(),
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(state = state, onEvent = { events += it }, onBack = {}, composerText = text)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a long press on a bubble opens its actions`() {
        setScreen(ChatPreviewData.privateChat)

        composeRule.onNodeWithContentDescription(ADA_LAST).performTouchInput { longClick() }

        assertEquals(listOf(ChatEvent.ShowMessageActions(11)), events.filterIsInstance<ChatEvent.ShowMessageActions>())
    }

    @Test
    fun `a long press on a photo opens its actions instead of the viewer`() {
        setScreen(ChatPreviewData.mediaPhotos)

        // On the photo, at the start of the row.
        composeRule.onNodeWithContentDescription("Ada, 9:40 AM: Photo").performTouchInput {
            longClick(percentOffset(PHOTO_X, PHOTO_Y))
        }

        assertEquals(listOf(ChatEvent.ShowMessageActions(48)), events.filterIsInstance<ChatEvent.ShowMessageActions>())
        assertEquals(emptyList<ChatEvent>(), events.filterIsInstance<ChatEvent.OpenMedia>())
    }

    @Test
    fun `a tap on a photo still opens it`() {
        setScreen(ChatPreviewData.mediaPhotos)

        composeRule.onNodeWithContentDescription("Ada, 9:40 AM: Photo").performTouchInput {
            click(percentOffset(PHOTO_X, PHOTO_Y))
        }

        assertEquals(listOf(ChatEvent.OpenMedia(48)), events.filterIsInstance<ChatEvent.OpenMedia>())
        assertEquals(emptyList<ChatEvent>(), events.filterIsInstance<ChatEvent.ShowMessageActions>())
    }

    @Test
    fun `screen readers open the actions with the bubble's long click`() {
        setScreen(ChatPreviewData.privateChat)

        composeRule.onNodeWithContentDescription(ADA_LAST).performSemanticsAction(SemanticsActions.OnLongClick)

        assertEquals(listOf(ChatEvent.ShowMessageActions(11)), events.filterIsInstance<ChatEvent.ShowMessageActions>())
    }

    @Test
    fun `the sheet lists the actions and sends the one chosen`() {
        setScreen(ChatPreviewData.ownActionsSheet)
        listOf(
            R.string.message_action_reply,
            R.string.message_action_copy,
            R.string.message_action_pin,
            R.string.message_action_pin_for_self,
            R.string.message_action_delete,
        ).forEach { composeRule.onNodeWithText(string(it)).assertIsDisplayed() }

        composeRule.onNodeWithText(string(R.string.message_action_edit)).performClick()
        composeRule.waitForIdle()

        assertEquals(ChatEvent.Edit(6), events.last())
    }

    @Test
    fun `pinning only for oneself sends that`() {
        setScreen(ChatPreviewData.ownActionsSheet)

        composeRule.onNodeWithText(string(R.string.message_action_pin_for_self)).performClick()
        composeRule.waitForIdle()

        assertEquals(ChatEvent.Pin(6, onlyForSelf = true), events.last())
    }

    @Test
    fun `someone else's message offers no editing`() {
        setScreen(ChatPreviewData.incomingActionsSheet)

        composeRule.onNodeWithText(string(R.string.message_action_reply)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.message_action_edit)).assertDoesNotExist()
    }

    @Test
    fun `a quick reaction goes to the message`() {
        setScreen(ChatPreviewData.ownActionsSheet)

        composeRule.onNodeWithContentDescription(string(R.string.message_reaction_add, "👍")).performClick()
        composeRule.waitForIdle()

        assertEquals(ChatEvent.ToggleReaction(6, ReactionLabel.Emoji("👍")), events.last())
    }

    @Test
    fun `a chosen quick reaction says it takes the reaction back`() {
        setScreen(ChatPreviewData.incomingActionsSheet)

        composeRule.onNodeWithContentDescription(string(R.string.message_reaction_remove, "🔥")).performClick()
        composeRule.waitForIdle()

        assertEquals(ChatEvent.ToggleReaction(24, ReactionLabel.Emoji("🔥")), events.last())
    }

    @Test
    fun `the button after the quick reactions opens the picker`() {
        setScreen(ChatPreviewData.ownActionsSheet)

        composeRule.onNodeWithContentDescription(string(R.string.message_reactions_more)).performClick()

        assertEquals(ChatEvent.ExpandReactions, events.last())
    }

    @Test
    fun `a reaction of the picker goes to the message`() {
        setScreen(ChatPreviewData.reactionPickerSheet)

        composeRule.onNodeWithContentDescription(string(R.string.message_reaction_add, "😍")).performClick()
        composeRule.waitForIdle()

        assertEquals(ChatEvent.ToggleReaction(24, ReactionLabel.Emoji("😍")), events.last())
    }

    @Test
    fun `a reaction on the message, in the picker, says how often it was added and toggles`() {
        setScreen(ChatPreviewData.reactionPickerSheet)
        val chip = string(R.string.message_reaction_remove, "🔥")
        val label =
            RuntimeEnvironment.getApplication().resources.getQuantityString(
                R.plurals.message_reaction_count,
                5,
                chip,
                5,
            )

        composeRule.onNodeWithContentDescription(label).performClick()
        composeRule.waitForIdle()

        assertEquals(ChatEvent.ToggleReaction(24, ReactionLabel.Emoji("🔥")), events.last())
    }

    @Test
    fun `the picker's back button returns to the actions`() {
        setScreen(ChatPreviewData.reactionPickerSheet)

        composeRule.onNodeWithText(string(R.string.message_action_reply)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.reaction_picker_back)).performClick()

        assertEquals(ChatEvent.CollapseReactions, events.last())
    }

    @Test
    fun `screen readers open all reactions where the bubble shows more than its badges`() {
        setScreen(chatWithReaction())

        performCustomAction("Ada, 9:41 AM: Hi", string(R.string.message_reactions_all))

        assertEquals(listOf(ChatEvent.ShowAllReactions(5)), events.filterIsInstance<ChatEvent.ShowAllReactions>())
    }

    @Test
    fun `deleting for the other person too takes the check box`() {
        setScreen(ChatPreviewData.deleteConfirmation)

        composeRule.onNodeWithText(string(R.string.message_delete_also_for, "Ada Lovelace")).performClick()
        composeRule.onNodeWithText(string(R.string.message_delete_confirm)).performClick()

        assertEquals(ChatEvent.ConfirmDelete(forEveryone = true), events.last())
    }

    @Test
    fun `deleting without the check box deletes for the user alone`() {
        setScreen(ChatPreviewData.deleteConfirmation)

        composeRule.onNodeWithText(string(R.string.message_delete_confirm)).performClick()

        assertEquals(ChatEvent.ConfirmDelete(forEveryone = false), events.last())
    }

    @Test
    fun `cancelling the confirmation deletes nothing`() {
        setScreen(ChatPreviewData.deleteConfirmation)

        composeRule.onNodeWithText(string(R.string.message_delete_cancel)).performClick()

        assertEquals(ChatEvent.DismissDelete, events.last())
    }

    @Test
    fun `an album's confirmation counts its messages and offers no check box`() {
        setScreen(ChatPreviewData.mediaPhotos.copy(deleteDialog = ChatPreviewData.deleteAlbumForEveryone))

        composeRule.onNodeWithText("Delete 4 messages?").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.message_delete_for_everyone_body)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.message_delete_for_everyone)).assertDoesNotExist()
    }

    @Test
    fun `the reply bar shows its message when tapped, and its close button ends the reply`() {
        setScreen(ChatPreviewData.replying)

        composeRule.onNode(hasClickLabel(string(R.string.composer_bar_show))).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.composer_reply_cancel)).performClick()

        assertEquals(listOf(ChatEvent.OpenReply(10), ChatEvent.CancelReply), events.filter { it.isBarEvent })
    }

    @Test
    fun `while editing, a check saves the edit in the send button's place`() {
        setScreen(ChatPreviewData.editing, ComposerText(FormattedText(ChatPreviewData.EDITED_TEXT, persistentListOf())))

        composeRule.onNodeWithText(string(R.string.composer_edit_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.composer_send)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.composer_edit_save)).performClick()

        assertEquals(listOf(ChatEvent.Send(SendOptions())), events.filterIsInstance<ChatEvent.Send>())
    }

    @Test
    fun `the edit bar's close button cancels the edit`() {
        setScreen(ChatPreviewData.editing, ComposerText(FormattedText(ChatPreviewData.EDITED_TEXT, persistentListOf())))

        composeRule.onNodeWithContentDescription(string(R.string.composer_edit_cancel)).performClick()

        assertEquals(ChatEvent.CancelEdit, events.last())
    }

    @Test
    fun `tapping a reaction badge toggles the user's reaction`() {
        setScreen(chatWithReaction())

        composeRule.onNodeWithContentDescription("Ada, 9:41 AM: Hi, 👍 3", substring = true).performTouchInput {
            // The first badge: wider than the short bubble, the badges start at the row's start, below the bubble.
            click(Offset(BADGE_X.toPx(), height - BADGE_FROM_BOTTOM.toPx()))
        }

        assertEquals(
            listOf(ChatEvent.ToggleReaction(5, ReactionLabel.Emoji("👍"))),
            events.filterIsInstance<ChatEvent.ToggleReaction>(),
        )
    }

    @Test
    fun `screen readers reply, copy and open the actions through the bubble`() {
        setScreen(ChatPreviewData.privateChat.copy(shortcuts = MessageShortcuts(canReply = true, canCopy = true)))

        performCustomAction(ADA_LAST, string(R.string.message_action_reply))
        performCustomAction(ADA_LAST, string(R.string.message_action_copy))
        performCustomAction(ADA_LAST, string(R.string.message_actions))

        assertEquals(
            listOf(ChatEvent.Reply(11), ChatEvent.Copy(11), ChatEvent.ShowMessageActions(11)),
            events.filter { it is ChatEvent.Reply || it is ChatEvent.Copy || it is ChatEvent.ShowMessageActions },
        )
    }

    @Test
    fun `screen readers send a failed message again, and get no reply on a message being sent`() {
        setScreen(ChatPreviewData.privateChat.copy(shortcuts = MessageShortcuts(canReply = true, canCopy = true)))

        performCustomAction(FAILED, string(R.string.message_action_resend))

        assertEquals(listOf(ChatEvent.Resend(8)), events.filterIsInstance<ChatEvent.Resend>())
        assertEquals(false, customActionsOf(PENDING).contains(string(R.string.message_action_reply)))
    }

    @Test
    fun `screen readers get no reply where the user cannot write, nor copy where saving is restricted`() {
        setScreen(ChatPreviewData.privateChat)

        val actions = customActionsOf(ADA_LAST)

        assertEquals(listOf(string(R.string.selection_start), string(R.string.message_actions)), actions)
    }

    @Test
    fun `screen readers toggle the reactions of a bubble`() {
        setScreen(ChatPreviewData.groupChat)
        val grace = "Grace Hopper, 10:12 AM"

        performCustomAction(grace, string(R.string.message_reaction_remove, "🔥"))
        performCustomAction(grace, string(R.string.message_reaction_add, "❤️"))

        assertEquals(
            listOf(
                ChatEvent.ToggleReaction(24, ReactionLabel.Emoji("🔥")),
                ChatEvent.ToggleReaction(24, ReactionLabel.Emoji("❤️")),
            ),
            events.filterIsInstance<ChatEvent.ToggleReaction>(),
        )
        assertEquals(
            true,
            customActionsOf(
                grace,
            ).contains(string(R.string.message_reaction_add, string(R.string.chat_reaction_custom_emoji))),
        )
    }

    /** One incoming text message, 5, with a reaction the user did not choose. */
    private fun chatWithReaction(): ChatUiState {
        val today = UiText.resource(R.string.chat_date_today)
        val text = FormattedText("Hi", persistentListOf())
        val bubble =
            ChatItem.Bubble(
                id = 5,
                isOutgoing = false,
                position = GroupPosition.Single,
                sender = UiText.Plain("Ada"),
                senderId = 2,
                showsSenderName = false,
                avatar = null,
                hasAvatarColumn = false,
                time = "9:41 AM",
                isEdited = false,
                status = null,
                forwardedFrom = null,
                reply = null,
                content = MessageContent.Text(text, linkPreview = null),
                contentLabel = null,
                text = text,
                spoilersRevealed = false,
                linkPreview = null,
                reactions =
                    persistentListOf(
                        ReactionChip(ReactionLabel.Emoji("👍"), 3, isChosen = false),
                        ReactionChip(ReactionLabel.Emoji("❤️"), 2, isChosen = false),
                        ReactionChip(ReactionLabel.Emoji("🔥"), 2, isChosen = false),
                        ReactionChip(ReactionLabel.Emoji("🎉"), 1, isChosen = false),
                        ReactionChip(ReactionLabel.Emoji("😮"), 1, isChosen = false),
                    ),
                date = today,
            )
        return ChatPreviewData.privateChat.copy(
            content =
                ChatContent.Content(
                    persistentListOf(bubble, ChatItem.DateSeparator(1, today)),
                    reachedStart = true,
                    isLoadingOlder = false,
                    olderError = null,
                ),
        )
    }

    private val ChatEvent.isBarEvent: Boolean
        get() = this is ChatEvent.OpenReply || this == ChatEvent.CancelReply

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)

    /** The labels of the custom actions of the bubble whose description starts with [description]. */
    private fun customActionsOf(description: String): List<String> =
        composeRule
            .onNodeWithContentDescription(description, substring = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .map { it.label }

    /** Does what the custom action [label] of the bubble whose description starts with [description] does. */
    private fun performCustomAction(
        description: String,
        label: String,
    ) {
        val node = composeRule.onNodeWithContentDescription(description, substring = true).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].single { it.label == label }
        composeRule.runOnIdle { action.action() }
    }

    /** A node that does something labelled [label] when clicked. */
    private fun hasClickLabel(label: String) =
        SemanticsMatcher("has the click label $label") { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.label == label
        }

    private companion object {
        /** Ada's newest message in [ChatPreviewData.privateChat], as screen readers hear it. */
        const val ADA_LAST = "Ada, 9:46 AM: See you there!"

        /** The message that failed to send, and the one being sent, in [ChatPreviewData.privateChat]. */
        const val FAILED = "You, 9:40 AM: This one didn't go through"
        const val PENDING = "You, 9:41 AM: Sending the slides now"

        /** A point on the photo of [ChatPreviewData.mediaPhotos]' message 48, as fractions of its row. */
        const val PHOTO_X = 0.25f
        const val PHOTO_Y = 0.5f

        /** Where the first reaction badge of [chatWithReaction]'s bubble is, from the row's start and bottom. */
        val BADGE_X = 14.dp
        val BADGE_FROM_BOTTOM = 14.dp
    }
}
