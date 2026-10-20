package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AvailableReactions
import dev.kyralabs.pingogram.core.domain.model.ChatDraft
import dev.kyralabs.pingogram.core.domain.model.ChatMemberStatus
import dev.kyralabs.pingogram.core.domain.model.DraftContent
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.InteractionInfo
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.Reaction
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.domain.model.ThumbnailFormat
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.CHANNEL_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.basicGroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.outgoing
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.supergroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatComposer
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The message actions of [ChatViewModel] (`REQUIREMENTS.md` FR-C6, FR-C7): the sheet with what TDLib allows, its quick
 * reactions and reactions picker, replying (with drafts and process death), copying, editing, pinning, deleting,
 * sending again, reaction chips, actions TDLib no longer allows, and failures.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatMessageActionsTest : ChatViewModelHarness() {
    private val formatted =
        FormattedText(
            "Hello #there",
            persistentListOf(TextEntity(0, 5, TextEntityType.Bold), TextEntity(6, 6, TextEntityType.Hashtag)),
        )

    /** The user's own message 1 and Ada's message 2 in the private chat with Ada. */
    private fun TestScope.privateChat(
        mine: Message = outgoing(1),
        draft: ChatDraft? = null,
        handle: SavedStateHandle = savedState,
    ): ChatViewModel {
        users.setUsers(user(ADA, firstName = "Ada", lastName = "Lovelace"), user(ME, firstName = "Me"))
        chats.setChats(chat(title = "Ada Lovelace", draft = draft))
        messages.setHistory(PRIVATE_CHAT, history(mine, message(2)))
        return viewModel(handle = handle)
    }

    private fun TestScope.type(
        viewModel: ChatViewModel,
        text: String,
    ) {
        viewModel.composerText.state.edit { replace(0, length, text) }
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private fun TestScope.waitForDraftSave() {
        advanceTimeBy(ChatComposer.DRAFT_SAVE_DELAY_MILLIS)
        runCurrent()
    }

    private val ChatViewModel.sheet get() = state.messageActions

    private val ChatViewModel.bar get() = (state.composer as? ComposerUiState.Available)?.bar

    private fun allowing(
        replied: Boolean = true,
        edited: Boolean = true,
        pinned: Boolean = true,
        forSelf: Boolean = true,
        forAll: Boolean = true,
        saved: Boolean = true,
        forwarded: Boolean = true,
    ) {
        messageActions.onProperties = {
            Outcome.Success(
                FakeMessageActionRepository.ALL_ALLOWED.copy(
                    canBeReplied = replied,
                    canBeEdited = edited,
                    canBePinned = pinned,
                    canBeDeletedOnlyForSelf = forSelf,
                    canBeDeletedForAllUsers = forAll,
                    canBeSaved = saved,
                    canBeForwarded = forwarded,
                ),
            )
        }
    }

    private fun reactions(vararg reactions: Reaction) =
        InteractionInfo(viewCount = 0, forwardCount = 0, replyCount = null, persistentListOf(*reactions))

    private fun photo(
        caption: String,
        aboveMedia: Boolean = false,
    ) = MessageContent.Photo(
        photo = Photo(sizes = persistentListOf(), minithumbnail = null),
        caption = text(caption),
        hasSpoiler = false,
        isSecret = false,
        showCaptionAboveMedia = aboveMedia,
    )

    @Nested
    inner class Sheet {
        @Test
        fun `a long press opens the sheet with every action TDLib allows for the user's own message`() =
            runViewModelTest {
                val viewModel = privateChat()

                send(viewModel, ChatEvent.ShowMessageActions(1))

                assertEquals(
                    listOf(
                        MessageAction.Reply,
                        MessageAction.Copy,
                        MessageAction.Forward,
                        MessageAction.Edit,
                        MessageAction.Pin,
                        MessageAction.PinForSelf,
                        MessageAction.Delete,
                    ),
                    viewModel.sheet?.actions,
                )
                assertEquals(listOf(1L), messageActions.propertyRequests)
            }

        @Test
        fun `an action TDLib does not allow is not offered`() =
            runViewModelTest {
                allowing(edited = false, pinned = false)
                val viewModel = privateChat()

                send(viewModel, ChatEvent.ShowMessageActions(2))

                assertEquals(
                    listOf(MessageAction.Reply, MessageAction.Copy, MessageAction.Forward, MessageAction.Delete),
                    viewModel.sheet?.actions,
                )
            }

        @Test
        fun `nothing allowed offers nothing`() =
            runViewModelTest {
                allowing(
                    replied = false,
                    edited = false,
                    pinned = false,
                    forSelf = false,
                    forAll = false,
                    saved = false,
                    forwarded = false,
                )
                val viewModel = privateChat()

                send(viewModel, ChatEvent.ShowMessageActions(2))

                assertEquals(emptyList<MessageAction>(), viewModel.sheet?.actions)
            }

        @Test
        fun `a text whose chat protects its content cannot be copied`() =
            runViewModelTest {
                allowing(saved = false)
                val viewModel = privateChat()

                send(viewModel, ChatEvent.ShowMessageActions(2))

                assertEquals(false, MessageAction.Copy in viewModel.sheet!!.actions)
            }

        @Test
        fun `a message without a text or caption has nothing to copy or edit`() =
            runViewModelTest {
                val sticker = outgoing(1).copy(content = MessageContent.Dice("🎲", value = 3))
                val viewModel = privateChat(mine = sticker)

                send(viewModel, ChatEvent.ShowMessageActions(1))

                assertEquals(
                    listOf(
                        MessageAction.Reply,
                        MessageAction.Forward,
                        MessageAction.Pin,
                        MessageAction.PinForSelf,
                        MessageAction.Delete,
                    ),
                    viewModel.sheet?.actions,
                )
            }

        @Test
        fun `a pinned message offers unpinning instead`() =
            runViewModelTest {
                val viewModel = privateChat(mine = outgoing(1).copy(isPinned = true))

                send(viewModel, ChatEvent.ShowMessageActions(1))

                assertEquals(
                    listOf(
                        MessageAction.Reply,
                        MessageAction.Copy,
                        MessageAction.Forward,
                        MessageAction.Edit,
                        MessageAction.Unpin,
                        MessageAction.Delete,
                    ),
                    viewModel.sheet?.actions,
                )
            }

        @Test
        fun `a group offers no pinning for oneself`() =
            runViewModelTest {
                groups.basicGroupMap.value = mapOf(ChatFixtures.BASIC_GROUP_ID to basicGroup(3))
                val viewModel = showing(groupChat, history(message(1, GROUP_CHAT)))

                send(viewModel, ChatEvent.ShowMessageActions(1))

                assertEquals(false, MessageAction.PinForSelf in viewModel.sheet!!.actions)
            }

        @Test
        fun `Saved Messages offer no pinning for oneself`() =
            runViewModelTest {
                val saved = chat(id = ME)
                val viewModel = showing(saved, history(outgoing(1, chatId = ME)))

                send(viewModel, ChatEvent.ShowMessageActions(1))

                assertEquals(false, MessageAction.PinForSelf in viewModel.sheet!!.actions)
            }

        @Test
        fun `where the user cannot write, there is no replying or editing`() =
            runViewModelTest {
                groups.supergroupMap.value = mapOf(ChatFixtures.SUPERGROUP_ID to supergroup(9))
                val viewModel = showing(channelChat, history(message(1, CHANNEL_CHAT)))

                send(viewModel, ChatEvent.ShowMessageActions(1))

                assertEquals(
                    listOf(MessageAction.Copy, MessageAction.Forward, MessageAction.Pin, MessageAction.Delete),
                    viewModel.sheet?.actions,
                )
            }

        @Test
        fun `a failed message offers sending it again first`() =
            runViewModelTest {
                val failed = SendingState.Failed(DomainError.Network, canRetry = true, retryAfter = null)
                val viewModel = privateChat(mine = outgoing(1, sendingState = failed))

                send(viewModel, ChatEvent.ShowMessageActions(1))

                assertEquals(MessageAction.Resend, viewModel.sheet?.actions?.first())
            }

        @Test
        fun `a failed message TDLib cannot send again is not offered for it`() =
            runViewModelTest {
                val failed = SendingState.Failed(DomainError.Network, canRetry = false, retryAfter = null)
                val viewModel = privateChat(mine = outgoing(1, sendingState = failed))

                send(viewModel, ChatEvent.ShowMessageActions(1))

                assertEquals(false, MessageAction.Resend in viewModel.sheet!!.actions)
            }

        @Test
        fun `the quick reactions are TDLib's top ones, those the user chose checked`() =
            runViewModelTest {
                messageActions.reactions =
                    Outcome.Success(
                        AvailableReactions(
                            persistentListOf(
                                ReactionType.Emoji("👍"),
                                ReactionType.Emoji("🔥"),
                                ReactionType.CustomEmoji(5),
                            ),
                        ),
                    )
                val chosen = Reaction(ReactionType.Emoji("🔥"), totalCount = 2, isChosen = true)
                val viewModel = privateChat(mine = outgoing(1).copy(interactionInfo = reactions(chosen)))

                send(viewModel, ChatEvent.ShowMessageActions(1))

                assertEquals(
                    listOf(
                        ReactionOption(ReactionLabel.Emoji("👍"), isChosen = false),
                        ReactionOption(ReactionLabel.Emoji("🔥"), isChosen = true),
                        ReactionOption(ReactionLabel.CustomEmoji(5), isChosen = false),
                    ),
                    viewModel.sheet?.reactions,
                )
            }

        @Test
        fun `reactions TDLib does not give leave the row out, not the sheet`() =
            runViewModelTest {
                messageActions.reactions = Outcome.Failure(DomainError.Network)
                val viewModel = privateChat()

                send(viewModel, ChatEvent.ShowMessageActions(1))

                assertEquals(emptyList<ReactionOption>(), viewModel.sheet?.reactions)
                assertEquals(MessageAction.Reply, viewModel.sheet?.actions?.first())
            }

        @Test
        fun `an album's reactions go to its newest item`() =
            runViewModelTest {
                val album =
                    (3L..5L).map {
                        message(
                            it,
                            mediaAlbumId = 9,
                            content =
                                photo(
                                    if (it ==
                                        3L
                                    ) {
                                        "Trip"
                                    } else {
                                        ""
                                    },
                                ),
                        )
                    }
                chats.setChats(chat())
                messages.setHistory(PRIVATE_CHAT, history(*album.toTypedArray()))
                val viewModel = viewModel()

                send(viewModel, ChatEvent.ShowMessageActions(3))

                assertEquals(5L, viewModel.sheet?.reactionsMessageId)
                assertEquals(listOf(5L), messageActions.reactionRequests)
            }

        @Test
        fun `properties TDLib cannot give are a snackbar instead of the sheet`() =
            runViewModelTest {
                messageActions.onProperties = { Outcome.Failure(DomainError.NotFound) }
                val viewModel = privateChat()

                viewModel.effects.test {
                    send(viewModel, ChatEvent.ShowMessageActions(1))

                    assertEquals(ChatEffect.ShowError(DomainError.NotFound), awaitItem())
                }
                assertNull(viewModel.sheet)
            }

        @Test
        fun `dismissing closes the sheet`() =
            runViewModelTest {
                val viewModel = privateChat()
                send(viewModel, ChatEvent.ShowMessageActions(1))

                send(viewModel, ChatEvent.DismissMessageActions)

                assertNull(viewModel.sheet)
            }

        @Test
        fun `a message deleted while its sheet is open takes the sheet with it`() =
            runViewModelTest {
                val viewModel = privateChat()
                send(viewModel, ChatEvent.ShowMessageActions(1))

                messages.setHistory(PRIVATE_CHAT, history(message(2)))
                runCurrent()

                assertNull(viewModel.sheet)
            }
    }

    @Nested
    inner class Replying {
        @Test
        fun `replying shows the reply bar with the sender and the message, and focuses the composer`() =
            runViewModelTest {
                val viewModel = privateChat()

                viewModel.effects.test {
                    send(viewModel, ChatEvent.ShowMessageActions(2), ChatEvent.Reply(2))

                    assertEquals(ChatEffect.FocusComposer, awaitItem())
                }
                assertNull(viewModel.sheet)
                assertEquals(
                    ComposerBar.Reply(2, UiText.Plain("Ada Lovelace"), UiText.Plain("Message 2")),
                    viewModel.bar,
                )
            }

        @Test
        fun `a reply to the user's own message says You`() =
            runViewModelTest {
                val viewModel = privateChat()

                send(viewModel, ChatEvent.Reply(1))

                assertEquals(
                    UiText.resource(UiR.string.preview_sender_you),
                    (viewModel.bar as ComposerBar.Reply).sender,
                )
            }

        @Test
        fun `the message goes as a reply, and the bar closes`() =
            runViewModelTest {
                val viewModel = privateChat()
                send(viewModel, ChatEvent.Reply(2))
                type(viewModel, "Sure")

                send(viewModel, ChatEvent.Send())

                assertEquals(listOf<Long?>(2), messages.sentReplies)
                assertNull(viewModel.bar)
            }

        @Test
        fun `a refused reply comes back with its bar`() =
            runViewModelTest {
                messages.onSendText = { Outcome.Failure(DomainError.Network) }
                val viewModel = privateChat()
                send(viewModel, ChatEvent.Reply(2))
                type(viewModel, "Sure")

                send(viewModel, ChatEvent.Send())

                assertEquals(text("Sure"), viewModel.composerText.content)
                assertEquals(2L, viewModel.bar?.messageId)
            }

        @Test
        fun `cancelling the reply sends the next message as no reply`() =
            runViewModelTest {
                val viewModel = privateChat()
                send(viewModel, ChatEvent.Reply(2), ChatEvent.CancelReply)
                type(viewModel, "Hi")

                send(viewModel, ChatEvent.Send())

                assertNull(viewModel.bar)
                assertEquals(listOf<Long?>(null), messages.sentReplies)
            }

        @Test
        fun `the draft keeps the reply, also without a text`() =
            runViewModelTest {
                val viewModel = privateChat()

                send(viewModel, ChatEvent.Reply(2))
                waitForDraftSave()

                assertEquals(listOf(PRIVATE_CHAT to FormattedText.Empty), messages.drafts)
                assertEquals(listOf<Long?>(2), messages.draftReplies)
            }

        @Test
        fun `a draft's reply comes back when the chat opens`() =
            runViewModelTest {
                val draft = ChatDraft(DraftContent.Text(text("Sure")), date = 0, replyToMessageId = 2)

                val viewModel = privateChat(draft = draft)

                assertEquals(text("Sure"), viewModel.composerText.content)
                assertEquals(2L, viewModel.bar?.messageId)
            }

        @Test
        fun `a reply to a message that is not loaded fetches it for the bar`() =
            runViewModelTest {
                val older = message(7, sender = MessageSender.User(ADA))
                messages.onMessage = { _, _ -> Outcome.Success(older) }
                val draft = ChatDraft(DraftContent.Text(FormattedText.Empty), date = 0, replyToMessageId = 7)

                val viewModel = privateChat(draft = draft)

                assertEquals(listOf(PRIVATE_CHAT to 7L), messages.messageRequests)
                assertEquals(
                    ComposerBar.Reply(7, UiText.Plain("Ada Lovelace"), UiText.Plain("Message 7")),
                    viewModel.bar,
                )
            }

        @Test
        fun `a reply to a deleted message ends`() =
            runViewModelTest {
                val viewModel = privateChat()
                send(viewModel, ChatEvent.Reply(2))

                messages.setHistory(PRIVATE_CHAT, history(outgoing(1)))
                runCurrent()

                assertNull(viewModel.bar)
            }

        @Test
        fun `the reply survives process death`() =
            runViewModelTest {
                val before = privateChat()
                send(before, ChatEvent.Reply(2))
                clear(before)

                val after =
                    privateChat(handle = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

                assertEquals(2L, after.bar?.messageId)
            }
    }

    @Nested
    inner class Copying {
        @Test
        fun `copying puts the plain text on the clipboard`() =
            runViewModelTest {
                val viewModel = privateChat(mine = outgoing(1).copy(content = MessageContent.Text(formatted, null)))

                viewModel.effects.test {
                    send(viewModel, ChatEvent.Copy(1))

                    assertEquals(ChatEffect.CopyText("Hello #there"), awaitItem())
                }
            }

        @Test
        fun `copying a photo takes its caption`() =
            runViewModelTest {
                val viewModel = privateChat(mine = outgoing(1).copy(content = photo("Sunset")))

                viewModel.effects.test {
                    send(viewModel, ChatEvent.Copy(1))

                    assertEquals(ChatEffect.CopyText("Sunset"), awaitItem())
                }
            }
    }

    @Nested
    inner class Editing {
        @Test
        fun `editing fills the composer with the text and the formatting a sender sets, under an edit bar`() =
            runViewModelTest {
                val viewModel = privateChat(mine = outgoing(1).copy(content = MessageContent.Text(formatted, null)))

                send(viewModel, ChatEvent.Edit(1))

                assertEquals(
                    FormattedText("Hello #there", persistentListOf(TextEntity(0, 5, TextEntityType.Bold))),
                    viewModel.composerText.content,
                )
                assertEquals(ComposerBar.Edit(1, UiText.Plain("Hello #there"), canBeEmpty = false), viewModel.bar)
            }

        @Test
        fun `saving sends the edit with its formatting and gives the composer back`() =
            runViewModelTest {
                val viewModel = privateChat()
                send(viewModel, ChatEvent.Reply(2))
                type(viewModel, "Draft")
                send(viewModel, ChatEvent.Edit(1))
                viewModel.composerText.setContent(formatted)

                send(viewModel, ChatEvent.Send())

                assertEquals(listOf("editText:$PRIVATE_CHAT:1"), messageActions.calls)
                assertEquals(listOf(formatted), messageActions.edits)
                assertEquals(text("Draft"), viewModel.composerText.content)
                assertEquals(2L, (viewModel.bar as ComposerBar.Reply).messageId)
                assertEquals(emptyList<Any>(), messages.sent)
            }

        @Test
        fun `a caption is edited where it is shown`() =
            runViewModelTest {
                val viewModel = privateChat(mine = outgoing(1).copy(content = photo("Sunset", aboveMedia = true)))
                send(viewModel, ChatEvent.Edit(1))
                type(viewModel, "Sunrise")

                send(viewModel, ChatEvent.Send())

                assertEquals(listOf("editCaption:$PRIVATE_CHAT:1:true"), messageActions.calls)
                assertNull(viewModel.bar)
            }

        @Test
        fun `a caption can be emptied`() =
            runViewModelTest {
                val viewModel = privateChat(mine = outgoing(1).copy(content = photo("Sunset")))
                send(viewModel, ChatEvent.Edit(1))

                type(viewModel, "")

                assertEquals(true, (viewModel.bar as ComposerBar.Edit).canBeEmpty)
                send(viewModel, ChatEvent.Send())
                assertEquals(listOf(FormattedText.Empty), messageActions.edits)
            }

        @Test
        fun `an unchanged text sends nothing`() =
            runViewModelTest {
                val viewModel = privateChat()
                send(viewModel, ChatEvent.Edit(1))

                send(viewModel, ChatEvent.Send())

                assertEquals(emptyList<String>(), messageActions.calls)
                assertNull(viewModel.bar)
            }

        @Test
        fun `cancelling gives the composer back its text`() =
            runViewModelTest {
                val viewModel = privateChat()
                type(viewModel, "Half a thought")
                send(viewModel, ChatEvent.Edit(1))

                send(viewModel, ChatEvent.CancelEdit)

                assertEquals(text("Half a thought"), viewModel.composerText.content)
                assertNull(viewModel.bar)
                assertEquals(emptyList<String>(), messageActions.calls)
            }

        @Test
        fun `the draft is saved before the edit and left alone while editing`() =
            runViewModelTest {
                val viewModel = privateChat()
                type(viewModel, "Draft")

                send(viewModel, ChatEvent.Edit(1))
                type(viewModel, "Edited")
                waitForDraftSave()
                send(viewModel, ChatEvent.CancelEdit)
                waitForDraftSave()

                assertEquals(listOf(PRIVATE_CHAT to text("Draft")), messages.drafts)
            }

        @Test
        fun `a refused edit comes back with a snackbar`() =
            runViewModelTest {
                messageActions.onAction = { Outcome.Failure(DomainError.FloodWait(20)) }
                val viewModel = privateChat()

                viewModel.effects.test {
                    send(viewModel, ChatEvent.Edit(1))
                    assertEquals(ChatEffect.FocusComposer, awaitItem())
                    type(viewModel, "Edited")
                    send(viewModel, ChatEvent.Send())

                    assertEquals(ChatEffect.ShowError(DomainError.FloodWait(20)), awaitItem())
                }
                assertEquals(text("Edited"), viewModel.composerText.content)
                assertEquals(1L, (viewModel.bar as ComposerBar.Edit).messageId)
            }

        @Test
        fun `the edit and what it set aside survive process death`() =
            runViewModelTest {
                val before = privateChat()
                type(before, "Draft")
                send(before, ChatEvent.Edit(1))
                type(before, "Edited")
                clear(before)

                val after =
                    privateChat(handle = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))
                send(after, ChatEvent.CancelEdit)

                assertEquals(text("Draft"), after.composerText.content)
            }

        @Test
        fun `replying during an edit cancels the edit`() =
            runViewModelTest {
                val viewModel = privateChat()
                type(viewModel, "Draft")
                send(viewModel, ChatEvent.Edit(1))

                send(viewModel, ChatEvent.Reply(2))

                assertEquals(text("Draft"), viewModel.composerText.content)
                assertEquals(2L, (viewModel.bar as ComposerBar.Reply).messageId)
            }
    }

    @Nested
    inner class PinningDeletingResending {
        @Test
        fun `pinning pins for everyone, or only for the user`() =
            runViewModelTest {
                val viewModel = privateChat()

                send(viewModel, ChatEvent.Pin(2), ChatEvent.Pin(1, onlyForSelf = true))

                assertEquals(listOf("pin:$PRIVATE_CHAT:2:false", "pin:$PRIVATE_CHAT:1:true"), messageActions.calls)
            }

        @Test
        fun `unpinning a pinned message`() =
            runViewModelTest {
                val viewModel = privateChat(mine = outgoing(1).copy(isPinned = true))

                send(viewModel, ChatEvent.Unpin(1))

                assertEquals(listOf("unpin:$PRIVATE_CHAT:1"), messageActions.calls)
            }

        @Test
        fun `deleting in a private chat asks, offering to delete for the other person too`() =
            runViewModelTest {
                val viewModel = privateChat()

                send(viewModel, ChatEvent.Delete(2))

                assertEquals(
                    DeleteMessagesDialog(
                        persistentListOf(2),
                        DeleteScope.Choice(
                            UiText.resource(R.string.message_delete_also_for, UiText.Plain("Ada Lovelace")),
                        ),
                    ),
                    viewModel.state.deleteDialog,
                )
                assertEquals(emptyList<String>(), messageActions.calls)
            }

        @Test
        fun `a group offers deleting for everyone`() =
            runViewModelTest {
                groups.basicGroupMap.value = mapOf(ChatFixtures.BASIC_GROUP_ID to basicGroup(3))
                val viewModel = showing(groupChat, history(message(1, GROUP_CHAT)))

                send(viewModel, ChatEvent.Delete(1))

                assertEquals(
                    DeleteScope.Choice(UiText.resource(R.string.message_delete_for_everyone)),
                    viewModel.state.deleteDialog?.scope,
                )
            }

        @Test
        fun `the check box decides whether everyone loses the message`() =
            runViewModelTest {
                val viewModel = privateChat()

                send(viewModel, ChatEvent.Delete(2), ChatEvent.ConfirmDelete(forEveryone = true))
                send(viewModel, ChatEvent.Delete(1), ChatEvent.ConfirmDelete(forEveryone = false))

                assertEquals(
                    listOf("delete:$PRIVATE_CHAT:[2]:true", "delete:$PRIVATE_CHAT:[1]:false"),
                    messageActions.calls,
                )
                assertNull(viewModel.state.deleteDialog)
            }

        @Test
        fun `where messages are always deleted for everyone, they are`() =
            runViewModelTest {
                allowing(forSelf = false)
                val viewModel = privateChat()

                send(viewModel, ChatEvent.Delete(2))
                val scope = viewModel.state.deleteDialog?.scope
                send(viewModel, ChatEvent.ConfirmDelete(forEveryone = false))

                assertEquals(DeleteScope.ForEveryone, scope)
                assertEquals(listOf("delete:$PRIVATE_CHAT:[2]:true"), messageActions.calls)
            }

        @Test
        fun `where messages can only be deleted for the user, they are`() =
            runViewModelTest {
                allowing(forAll = false)
                val viewModel = privateChat()

                send(viewModel, ChatEvent.Delete(2))
                val scope = viewModel.state.deleteDialog?.scope
                send(viewModel, ChatEvent.ConfirmDelete(forEveryone = true))

                assertEquals(DeleteScope.ForMe, scope)
                assertEquals(listOf("delete:$PRIVATE_CHAT:[2]:false"), messageActions.calls)
            }

        @Test
        fun `an album is deleted as a whole`() =
            runViewModelTest {
                val album = (3L..5L).map { message(it, mediaAlbumId = 9, content = photo("")) }
                chats.setChats(chat(title = "Ada Lovelace"))
                messages.setHistory(PRIVATE_CHAT, history(*album.toTypedArray()))
                val viewModel = viewModel()

                send(viewModel, ChatEvent.Delete(5), ChatEvent.ConfirmDelete(forEveryone = true))

                assertEquals(listOf("delete:$PRIVATE_CHAT:[3, 4, 5]:true"), messageActions.calls)
            }

        @Test
        fun `dismissing the confirmation deletes nothing`() =
            runViewModelTest {
                val viewModel = privateChat()

                send(viewModel, ChatEvent.Delete(2), ChatEvent.DismissDelete)

                assertNull(viewModel.state.deleteDialog)
                assertEquals(emptyList<String>(), messageActions.calls)
            }

        @Test
        fun `a failed message is sent again`() =
            runViewModelTest {
                val failed = SendingState.Failed(DomainError.Network, canRetry = true, retryAfter = null)
                val viewModel = privateChat(mine = outgoing(1, sendingState = failed))

                send(viewModel, ChatEvent.Resend(1))

                assertEquals(listOf("resend:$PRIVATE_CHAT:[1]"), messageActions.calls)
            }

        @Test
        fun `a flood wait is a snackbar with its countdown`() =
            runViewModelTest {
                messageActions.onAction = { Outcome.Failure(DomainError.FloodWait(42)) }
                val viewModel = privateChat()

                viewModel.effects.test {
                    send(viewModel, ChatEvent.Pin(2))

                    assertEquals(ChatEffect.ShowError(DomainError.FloodWait(42)), awaitItem())
                }
            }

        @Test
        fun `an action TDLib no longer allows only says so`() =
            runViewModelTest {
                allowing(edited = false)
                val viewModel = privateChat()

                viewModel.effects.test {
                    send(viewModel, ChatEvent.Edit(1))

                    assertEquals(ChatEffect.ActionUnavailable, awaitItem())
                }
                assertNull(viewModel.bar)
            }

        @Test
        fun `a group the user left gets no reply started`() =
            runViewModelTest {
                groups.basicGroupMap.value =
                    mapOf(ChatFixtures.BASIC_GROUP_ID to basicGroup(3, status = ChatMemberStatus.Left))
                val viewModel = showing(groupChat, history(message(1, GROUP_CHAT)))

                viewModel.effects.test {
                    send(viewModel, ChatEvent.Reply(1))

                    assertEquals(ChatEffect.ActionUnavailable, awaitItem())
                }
            }
    }

    @Nested
    inner class Reactions {
        @Test
        fun `a reaction the user did not choose is added`() =
            runViewModelTest {
                val others = Reaction(ReactionType.Emoji("👍"), totalCount = 1, isChosen = false)
                val viewModel = privateChat(mine = outgoing(1).copy(interactionInfo = reactions(others)))

                send(viewModel, ChatEvent.ToggleReaction(1, ReactionLabel.Emoji("👍")))

                assertEquals(listOf("addReaction:$PRIVATE_CHAT:1:Emoji(emoji=👍)"), messageActions.calls)
            }

        @Test
        fun `a reaction the user chose is taken back`() =
            runViewModelTest {
                val mine = Reaction(ReactionType.CustomEmoji(5), totalCount = 2, isChosen = true)
                val viewModel = privateChat(mine = outgoing(1).copy(interactionInfo = reactions(mine)))

                send(viewModel, ChatEvent.ToggleReaction(1, ReactionLabel.CustomEmoji(5)))

                assertEquals(
                    listOf("removeReaction:$PRIVATE_CHAT:1:CustomEmoji(customEmojiId=5)"),
                    messageActions.calls,
                )
            }

        @Test
        fun `a quick reaction closes the sheet`() =
            runViewModelTest {
                val viewModel = privateChat()
                send(viewModel, ChatEvent.ShowMessageActions(2))

                send(viewModel, ChatEvent.ToggleReaction(2, ReactionLabel.Emoji("🔥")))

                assertNull(viewModel.sheet)
                assertEquals(listOf("addReaction:$PRIVATE_CHAT:2:Emoji(emoji=🔥)"), messageActions.calls)
            }

        @Test
        fun `the paid reaction is not changed here`() =
            runViewModelTest {
                val viewModel = privateChat()

                send(viewModel, ChatEvent.ToggleReaction(2, ReactionLabel.Paid))

                assertEquals(emptyList<String>(), messageActions.calls)
            }

        @Test
        fun `a refused reaction is a snackbar`() =
            runViewModelTest {
                messageActions.onAction = { Outcome.Failure(DomainError.Network) }
                val viewModel = privateChat()

                viewModel.effects.test {
                    send(viewModel, ChatEvent.ToggleReaction(2, ReactionLabel.Emoji("🔥")))

                    assertEquals(ChatEffect.ShowError(DomainError.Network), awaitItem())
                }
            }
    }

    @Nested
    inner class ReactionsPicker {
        private fun emoji(vararg emoji: String) = emoji.map { ReactionType.Emoji(it) }.toImmutableList()

        private fun options(
            vararg emoji: String,
            chosen: String? = null,
        ) = emoji.map { ReactionOption(ReactionLabel.Emoji(it), isChosen = it == chosen) }

        private fun still(fileId: Int) =
            Sticker(0, 100, 100, "😎", StickerFormat.Webp, thumbnail = null, file = FileRef(fileId, 0))

        private fun animated(thumbnail: Thumbnail?) =
            Sticker(0, 100, 100, "😎", StickerFormat.Tgs, thumbnail = thumbnail, file = FileRef(99, 0))

        @Test
        fun `the picker has TDLib's sections after the message's own reactions, those the user chose checked`() =
            runViewModelTest {
                messageActions.reactions =
                    Outcome.Success(
                        AvailableReactions(
                            top = emoji("👍", "🔥"),
                            recent = emoji("😍"),
                            popular = emoji("🎉", "🤩"),
                        ),
                    )
                val chosen = Reaction(ReactionType.Emoji("🔥"), totalCount = 2, isChosen = true)
                val others = Reaction(ReactionType.Emoji("❤️"), totalCount = 1, isChosen = false)
                val viewModel = privateChat(mine = outgoing(1).copy(interactionInfo = reactions(chosen, others)))

                send(viewModel, ChatEvent.ShowMessageActions(1))

                val sheet = viewModel.sheet!!
                assertEquals(options("👍", "🔥", chosen = "🔥"), sheet.reactions)
                assertEquals(false, sheet.showsPicker)
                assertEquals(
                    ReactionPicker(
                        onMessage =
                            persistentListOf(
                                ReactionChip(ReactionLabel.Emoji("🔥"), 2, isChosen = true),
                                ReactionChip(ReactionLabel.Emoji("❤️"), 1, isChosen = false),
                            ),
                        sections =
                            persistentListOf(
                                ReactionSection(
                                    ReactionSectionKind.Top,
                                    options("👍", "🔥", chosen = "🔥").toImmutableList(),
                                ),
                                ReactionSection(ReactionSectionKind.Recent, options("😍").toImmutableList()),
                                ReactionSection(ReactionSectionKind.Popular, options("🎉", "🤩").toImmutableList()),
                            ),
                    ),
                    sheet.picker,
                )
            }

        @Test
        fun `no picker where the quick row has every reaction and the bubble room for the message's`() =
            runViewModelTest {
                messageActions.reactions = Outcome.Success(AvailableReactions(top = emoji("👍", "🔥")))
                val viewModel = privateChat()

                send(viewModel, ChatEvent.ShowMessageActions(1), ChatEvent.ExpandReactions)

                assertNull(viewModel.sheet?.picker)
                assertEquals(false, viewModel.sheet?.showsPicker)
            }

        @Test
        fun `a message with more reactions than its badges has a picker, even with reactions off`() =
            runViewModelTest {
                val four = listOf("👍", "🔥", "🎉", "❤️").map { Reaction(ReactionType.Emoji(it), 1, isChosen = false) }
                val viewModel =
                    privateChat(mine = outgoing(1).copy(interactionInfo = reactions(*four.toTypedArray())))

                send(viewModel, ChatEvent.ShowMessageActions(1))

                assertEquals(emptyList<ReactionOption>(), viewModel.sheet?.reactions)
                assertEquals(
                    4,
                    viewModel.sheet
                        ?.picker
                        ?.onMessage
                        ?.size,
                )
                assertEquals(emptyList<ReactionSection>(), viewModel.sheet?.picker?.sections)
            }

        @Test
        fun `all reactions open the sheet at its picker, which switches to the actions and back`() =
            runViewModelTest {
                messageActions.reactions = Outcome.Success(AvailableReactions(top = emoji("👍"), popular = emoji("🎉")))
                val viewModel = privateChat()

                send(viewModel, ChatEvent.ShowAllReactions(2))
                assertEquals(true, viewModel.sheet?.showsPicker)
                assertEquals(2L, viewModel.sheet?.messageId)

                send(viewModel, ChatEvent.CollapseReactions)
                assertEquals(false, viewModel.sheet?.showsPicker)

                send(viewModel, ChatEvent.ExpandReactions)
                assertEquals(true, viewModel.sheet?.showsPicker)
            }

        @Test
        fun `all reactions without a picker open the actions`() =
            runViewModelTest {
                val viewModel = privateChat()

                send(viewModel, ChatEvent.ShowAllReactions(2))

                assertEquals(false, viewModel.sheet?.showsPicker)
                assertEquals(MessageAction.Reply, viewModel.sheet?.actions?.first())
            }

        @Test
        fun `a reaction from the picker goes to the message and closes the sheet`() =
            runViewModelTest {
                messageActions.reactions = Outcome.Success(AvailableReactions(top = emoji("👍"), popular = emoji("🎉")))
                val viewModel = privateChat()
                send(viewModel, ChatEvent.ShowAllReactions(2))

                send(viewModel, ChatEvent.ToggleReaction(2, ReactionLabel.Emoji("🎉")))

                assertNull(viewModel.sheet)
                assertEquals(listOf("addReaction:$PRIVATE_CHAT:2:Emoji(emoji=🎉)"), messageActions.calls)
            }

        @Test
        fun `custom emoji get the picture of their sticker once the sheet is open`() =
            runViewModelTest {
                messageActions.reactions =
                    Outcome.Success(
                        AvailableReactions(
                            top = persistentListOf(ReactionType.CustomEmoji(5), ReactionType.Emoji("👍")),
                            recent = persistentListOf(ReactionType.CustomEmoji(6), ReactionType.CustomEmoji(7)),
                        ),
                    )
                val onMessage = Reaction(ReactionType.CustomEmoji(8), totalCount = 1, isChosen = false)
                messageActions.customEmoji =
                    Outcome.Success(
                        mapOf(
                            5L to still(fileId = 50),
                            6L to animated(Thumbnail(ThumbnailFormat.Webp, 100, 100, FileRef(60, 0))),
                            // An animated thumbnail is no picture: the placeholder stays.
                            7L to animated(Thumbnail(ThumbnailFormat.Tgs, 100, 100, FileRef(70, 0))),
                            8L to still(fileId = 80),
                        ),
                    )
                val viewModel = privateChat(mine = outgoing(1).copy(interactionInfo = reactions(onMessage)))

                send(viewModel, ChatEvent.ShowMessageActions(1))

                assertEquals(listOf(listOf(5L, 6L, 7L, 8L)), messageActions.customEmojiRequests)
                assertEquals(mapOf(5L to 50, 6L to 60, 8L to 80), viewModel.sheet?.customEmojiPictures)
            }

        @Test
        fun `custom emoji TDLib cannot picture keep their placeholder, and the sheet opens anyway`() =
            runViewModelTest {
                messageActions.reactions =
                    Outcome.Success(AvailableReactions(top = persistentListOf(ReactionType.CustomEmoji(5))))
                messageActions.customEmoji = Outcome.Failure(DomainError.Network)
                val viewModel = privateChat()

                send(viewModel, ChatEvent.ShowMessageActions(1))

                assertEquals(emptyMap<Long, Int>(), viewModel.sheet?.customEmojiPictures)
                assertEquals(listOf(ReactionOption(ReactionLabel.CustomEmoji(5), false)), viewModel.sheet?.reactions)
            }

        @Test
        fun `a sheet without custom emoji asks for no pictures`() =
            runViewModelTest {
                messageActions.reactions = Outcome.Success(AvailableReactions(top = emoji("👍")))
                val viewModel = privateChat()

                send(viewModel, ChatEvent.ShowMessageActions(1))

                assertEquals(emptyList<List<Long>>(), messageActions.customEmojiRequests)
            }
    }

    @Nested
    inner class Shortcuts {
        @Test
        fun `bubbles offer replying where the user can write and copying where the chat allows saving`() =
            runViewModelTest {
                val viewModel = privateChat()

                assertEquals(MessageShortcuts(canReply = true, canCopy = true), viewModel.state.shortcuts)
            }

        @Test
        fun `a channel the user reads offers no replying`() =
            runViewModelTest {
                groups.supergroupMap.value = mapOf(ChatFixtures.SUPERGROUP_ID to supergroup(9))
                val viewModel = showing(channelChat, history(message(1, CHANNEL_CHAT)))

                assertEquals(MessageShortcuts(canReply = false, canCopy = true), viewModel.state.shortcuts)
            }

        @Test
        fun `a chat that protects its content offers no copying`() =
            runViewModelTest {
                val protected = chat().copy(hasProtectedContent = true)
                val viewModel = showing(protected, history(message(1)))

                assertEquals(false, viewModel.state.shortcuts.canCopy)
            }
    }
}
