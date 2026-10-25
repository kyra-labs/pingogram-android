package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.model.ChatMemberStatus
import dev.kyralabs.pingogram.core.domain.model.Document
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageLink
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.TextQuote
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.CHANNEL_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.FORUM_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.SUPERGROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.forumChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.outgoing
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.supergroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.composer.ReplaceMedia
import dev.kyralabs.pingogram.feature.chat.ui.reply.ReplyChatsSheet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Quote replies, replies in another chat, message links and replacing an edited message's media in [ChatViewModel]
 * (`REQUIREMENTS.md` FR-C5, FR-C6): what each sends, the reply and edit bars they show, the hand-over between chats,
 * drafts and process death, actions TDLib does not allow, and failures.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepliesTest : ChatViewModelHarness() {
    private val question = "Is the meetup still on for tonight?"

    /** Ada's question (message 2, "still on" in italics) and the user's message 1 in the private chat with Ada. */
    private fun TestScope.privateChat(
        mine: Message = outgoing(1),
        handle: SavedStateHandle = savedState,
    ): ChatViewModel {
        users.setUsers(user(ADA, firstName = "Ada", lastName = "Lovelace"), user(ME, firstName = "Me"))
        chats.setChats(chat(title = "Ada Lovelace"))
        val italic = TextEntity(question.indexOf("still"), "still on".length, TextEntityType.Italic)
        val link = TextEntity(0, 2, TextEntityType.Url)
        val text = FormattedText(question, persistentListOf(italic, link))
        messages.setHistory(PRIVATE_CHAT, history(mine, message(2, content = MessageContent.Text(text, null))))
        return viewModel(handle = handle)
    }

    private fun allowing(
        repliedElsewhere: Boolean = true,
        link: Boolean = true,
        editMedia: Boolean = true,
        replied: Boolean = true,
    ) {
        messageActions.onProperties = {
            Outcome.Success(
                FakeMessageActionRepository.ALL_ALLOWED.copy(
                    canBeReplied = replied,
                    canBeRepliedInAnotherChat = repliedElsewhere,
                    canGetLink = link,
                    canEditMedia = editMedia,
                ),
            )
        }
    }

    private fun TestScope.type(
        viewModel: ChatViewModel,
        text: String,
    ) {
        viewModel.composerText.state.edit { replace(0, length, text) }
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private val ChatViewModel.bar get() = (state.composer as? ComposerUiState.Available)?.bar

    private fun photo(caption: String) =
        MessageContent.Photo(
            photo = Photo(sizes = persistentListOf(), minithumbnail = null),
            caption = text(caption),
            hasSpoiler = false,
            isSecret = false,
            showCaptionAboveMedia = false,
        )

    @Nested
    inner class Quoting {
        @Test
        fun `Quote replies with the selected part, its formatting and position, and focuses the composer`() =
            runViewModelTest {
                val viewModel = privateChat()

                viewModel.effects.test {
                    send(viewModel, ReplyEvent.Quote(2, listOf("still on for")))

                    assertEquals(ChatEffect.FocusComposer, awaitItem())
                }

                assertEquals(
                    ComposerBar.Reply(2, UiText.Plain("Ada Lovelace"), UiText.Plain("still on for"), isQuote = true),
                    viewModel.bar,
                )
                type(viewModel, "Yes!")
                send(viewModel, ChatEvent.Send())
                val quote =
                    TextQuote(
                        FormattedText("still on for", persistentListOf(TextEntity(0, 8, TextEntityType.Italic))),
                        position = question.indexOf("still"),
                    )
                assertEquals(listOf(ReplyTarget(2, quote)), messages.sentReplyTargets)
            }

        @Test
        fun `a selection that starts in the sender's name quotes from the text's start, without white space`() =
            runViewModelTest {
                val viewModel = privateChat()

                send(viewModel, ReplyEvent.Quote(2, listOf("Ada", " Is the meetup ")))

                type(viewModel, "Yes")
                send(viewModel, ChatEvent.Send())
                assertEquals(TextQuote(text("Is the meetup"), 0), messages.sentReplyTargets.single()?.quote)
            }

        @Test
        fun `a selection outside the text quotes nothing and says so`() =
            runViewModelTest {
                val viewModel = privateChat()

                viewModel.effects.test {
                    send(viewModel, ReplyEvent.Quote(2, listOf("9:41 AM")))

                    assertEquals(ReplyEffect.QuoteUnavailable, awaitItem())
                }
                assertNull(viewModel.bar)
            }

        @Test
        fun `a message that cannot be replied to is not quoted`() =
            runViewModelTest {
                allowing(replied = false)
                val viewModel = privateChat()

                viewModel.effects.test {
                    send(viewModel, ReplyEvent.Quote(2, listOf("meetup")))

                    assertEquals(ChatEffect.ActionUnavailable, awaitItem())
                }
                assertNull(viewModel.bar)
            }

        @Test
        fun `the draft keeps the quote`() =
            runViewModelTest {
                val viewModel = privateChat()

                send(viewModel, ReplyEvent.Quote(2, listOf("tonight")))
                testScheduler.advanceTimeBy(DRAFT_DELAY)
                runCurrent()

                val quote = TextQuote(text("tonight"), question.indexOf("tonight"))
                assertEquals(quote, messages.draftReplyTargets.last()?.quote)
            }

        @Test
        fun `the quote survives process death`() =
            runViewModelTest {
                val before = privateChat()
                send(before, ReplyEvent.Quote(2, listOf("still on")))
                clear(before)

                val after =
                    privateChat(handle = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

                assertEquals(
                    ComposerBar.Reply(2, UiText.Plain("Ada Lovelace"), UiText.Plain("still on"), isQuote = true),
                    after.bar,
                )
            }

        @Test
        fun `a failed properties request is a snackbar`() =
            runViewModelTest {
                messageActions.onProperties = { Outcome.Failure(DomainError.FloodWait(5)) }
                val viewModel = privateChat()

                viewModel.effects.test {
                    send(viewModel, ReplyEvent.Quote(2, listOf("meetup")))

                    assertEquals(ChatEffect.ShowError(DomainError.FloodWait(5)), awaitItem())
                }
            }
    }

    @Nested
    inner class ReplyingElsewhere {
        @Test
        fun `the actions offer replying in another chat where TDLib allows it`() =
            runViewModelTest {
                allowing(link = false)
                val viewModel = privateChat()

                send(viewModel, ChatEvent.ShowMessageActions(2))

                val actions =
                    viewModel.state.messageActions
                        ?.actions
                        .orEmpty()
                assertEquals(MessageAction.ReplyElsewhere, actions[actions.indexOf(MessageAction.Reply) + 1])
            }

        @Test
        fun `it opens the chats where the user can write, and picking one hands the reply over and opens it`() =
            runViewModelTest {
                allowing()
                val viewModel = privateChat()
                chats.mainList.value = listOf(groupChat, chat(title = "Ada Lovelace"))
                groups.basicGroupMap.value = mapOf(ChatFixtures.BASIC_GROUP_ID to ChatFixtures.basicGroup(3))
                val sheet = backgroundCollect(viewModel)

                send(viewModel, ReplyEvent.ReplyElsewhere(2))
                assertEquals(2L, sheet()?.messageId)
                assertEquals(UiText.Plain(question), sheet()?.preview)
                assertTrue(sheet()?.targets.orEmpty().any { it.chatId == GROUP_CHAT })

                viewModel.effects.test {
                    send(viewModel, ReplyEvent.PickChat(GROUP_CHAT))

                    assertEquals(ChatEffect.OpenChat(GROUP_CHAT), awaitItem())
                }
                assertNull(sheet())
                assertNull(viewModel.bar)
            }

        @Test
        fun `picking this chat replies here`() =
            runViewModelTest {
                allowing()
                val viewModel = privateChat()
                backgroundCollect(viewModel)

                send(viewModel, ReplyEvent.ReplyElsewhere(2), ReplyEvent.PickChat(PRIVATE_CHAT))

                assertEquals(2L, viewModel.bar?.messageId)
            }

        @Test
        fun `dismissing the picker hands nothing over`() =
            runViewModelTest {
                allowing()
                val viewModel = privateChat()
                val sheet = backgroundCollect(viewModel)

                send(viewModel, ReplyEvent.ReplyElsewhere(2), ReplyEvent.DismissChats)

                assertNull(sheet())
            }

        @Test
        fun `TDLib not allowing it opens nothing`() =
            runViewModelTest {
                allowing(repliedElsewhere = false)
                val viewModel = privateChat()
                val sheet = backgroundCollect(viewModel)

                viewModel.effects.test {
                    send(viewModel, ReplyEvent.ReplyElsewhere(2))

                    assertEquals(ChatEffect.ActionUnavailable, awaitItem())
                }
                assertNull(sheet())
            }

        @Test
        fun `a reply handed over waits for the screen, then names the other chat and goes with the next message`() =
            runViewModelTest {
                users.setUsers(user(ADA, firstName = "Ada", lastName = "Lovelace"))
                chats.setChats(chat(title = "Ada Lovelace"), groupChat)
                messages.setHistory(PRIVATE_CHAT, history(message(1)))
                messages.onMessage = { chatId, id -> Outcome.Success(message(id, chatId = chatId)) }
                val viewModel = viewModel()
                val reply = ReplyTarget(25, chatId = GROUP_CHAT)

                shareInbox.deliverReply(PRIVATE_CHAT, reply)
                runCurrent()
                assertNull(viewModel.bar)
                send(viewModel, ChatEvent.Started)

                assertEquals(listOf(GROUP_CHAT to 25L), messages.messageRequests)
                assertEquals(
                    ComposerBar.Reply(
                        25,
                        res(
                            R.string.composer_reply_elsewhere,
                            UiText.Plain("Ada Lovelace").bidiIsolated(),
                            "Group".bidiIsolated(),
                        ),
                        UiText.Plain("Message 25"),
                        isElsewhere = true,
                    ),
                    viewModel.bar,
                )
                type(viewModel, "About that")
                send(viewModel, ChatEvent.Send())
                assertEquals(listOf<ReplyTarget?>(reply), messages.sentReplyTargets)
            }

        @Test
        fun `a reply handed over survives as a draft and process death`() =
            runViewModelTest {
                chats.setChats(chat(title = "Ada Lovelace"), groupChat)
                messages.setHistory(PRIVATE_CHAT, history(message(1)))
                messages.onMessage = { chatId, id -> Outcome.Success(message(id, chatId = chatId)) }
                val before = viewModel()
                shareInbox.deliverReply(PRIVATE_CHAT, ReplyTarget(25, chatId = GROUP_CHAT))
                send(before, ChatEvent.Started)
                testScheduler.advanceTimeBy(DRAFT_DELAY)
                runCurrent()
                clear(before)

                val restored = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) })
                val after = viewModel(handle = restored)

                assertEquals(ReplyTarget(25, chatId = GROUP_CHAT), messages.draftReplyTargets.last())
                assertEquals(true, (after.bar as? ComposerBar.Reply)?.isElsewhere)
            }
    }

    @Nested
    inner class Links {
        private fun TestScope.inChannel(album: Boolean = false): ChatViewModel {
            chats.setChats(channelChat)
            groups.supergroupMap.value = mapOf(SUPERGROUP_ID to supergroup(10, status = ChatMemberStatus.Member))
            val post = message(5, chatId = CHANNEL_CHAT, mediaAlbumId = if (album) 77 else 0)
            messages.setHistory(CHANNEL_CHAT, history(post))
            return viewModel(CHANNEL_CHAT)
        }

        @Test
        fun `Copy link copies TDLib's link of the message`() =
            runViewModelTest {
                allowing()
                val viewModel = inChannel()

                viewModel.effects.test {
                    send(viewModel, ReplyEvent.CopyLink(5))

                    assertEquals(ReplyEffect.CopyLink("https://t.me/c/5"), awaitItem())
                }
                assertEquals(listOf("link:$CHANNEL_CHAT:5:false:false"), messageActions.linkRequests)
            }

        @Test
        fun `an album's item copies the album's link, and a private link copies too`() =
            runViewModelTest {
                allowing()
                messageActions.onMessageLink = { id ->
                    Outcome.Success(MessageLink("https://t.me/c/1/$id", isPublic = false))
                }
                val viewModel = inChannel(album = true)

                viewModel.effects.test {
                    send(viewModel, ReplyEvent.CopyLink(5))

                    assertEquals(ReplyEffect.CopyLink("https://t.me/c/1/5"), awaitItem())
                }
                assertEquals(listOf("link:$CHANNEL_CHAT:5:true:false"), messageActions.linkRequests)
            }

        @Test
        fun `in a topic the action links to this thread`() =
            runViewModelTest {
                allowing()
                chats.setChats(forumChat)
                groups.supergroupMap.value =
                    mapOf(SUPERGROUP_ID to supergroup(10, isChannel = false, isForum = true))
                forumTopics.setTopics(FORUM_CHAT, ChatFixtures.forumTopic(7, name = "Help"))
                messages.setHistory(FORUM_CHAT, history(message(3, chatId = FORUM_CHAT)))
                val viewModel = viewModel(FORUM_CHAT, topic = MessageTopic.Forum(7))

                send(viewModel, ChatEvent.ShowMessageActions(3))
                assertTrue(
                    MessageAction.CopyThreadLink in
                        viewModel.state.messageActions
                            ?.actions
                            .orEmpty(),
                )
                assertEquals(true, viewModel.state.shortcuts.linksInThread)
                send(viewModel, ReplyEvent.CopyLink(3))

                assertEquals(listOf("link:$FORUM_CHAT:3:false:true"), messageActions.linkRequests)
            }

        @Test
        fun `a private chat offers no link`() =
            runViewModelTest {
                allowing(link = false)
                val viewModel = privateChat()

                send(viewModel, ChatEvent.ShowMessageActions(2))

                val actions =
                    viewModel.state.messageActions
                        ?.actions
                        .orEmpty()
                assertTrue(MessageAction.CopyLink !in actions && MessageAction.CopyThreadLink !in actions)
                assertEquals(false, viewModel.state.shortcuts.canCopyLink)
            }

        @Test
        fun `a link TDLib cannot make is a snackbar`() =
            runViewModelTest {
                for (error in listOf(DomainError.FloodWait(30), DomainError.Network, DomainError.Unauthorized)) {
                    allowing()
                    messageActions.onMessageLink = { Outcome.Failure(error) }
                    val viewModel = inChannel()

                    viewModel.effects.test {
                        send(viewModel, ReplyEvent.CopyLink(5))

                        assertEquals(ChatEffect.ShowError(error), awaitItem())
                    }
                }
            }
    }

    @Nested
    inner class ReplacingMedia {
        /** Editing the user's photo; the edit's request for the composer's focus is taken out of the effects. */
        private suspend fun TestScope.editingPhoto(canEditMedia: Boolean = true): ChatViewModel {
            allowing(editMedia = canEditMedia)
            val viewModel = privateChat(mine = outgoing(1).copy(content = photo("Sunset")))
            viewModel.effects.test {
                send(viewModel, ChatEvent.Edit(1))
                assertEquals(ChatEffect.FocusComposer, awaitItem())
            }
            return viewModel
        }

        @Test
        fun `editing a photo TDLib lets be replaced offers the Photo Picker`() =
            runViewModelTest {
                val viewModel = editingPhoto()

                assertEquals(ReplaceMedia.Media, (viewModel.bar as ComposerBar.Edit).replace)
                viewModel.effects.test {
                    send(viewModel, ReplyEvent.ReplaceMedia)

                    assertEquals(ReplyEffect.Pick(ReplaceMedia.Media), awaitItem())
                }
            }

        @Test
        fun `a file is replaced from the file picker`() =
            runViewModelTest {
                allowing()
                val document =
                    MessageContent.Document(
                        Document(
                            fileName = "notes.pdf",
                            mimeType = "application/pdf",
                            minithumbnail = null,
                            thumbnail = null,
                            file = FileRef(40, 1_000),
                        ),
                        caption = FormattedText.Empty,
                    )
                val viewModel = privateChat(mine = outgoing(1).copy(content = document))

                send(viewModel, ChatEvent.Edit(1))

                assertEquals(ReplaceMedia.Document, (viewModel.bar as ComposerBar.Edit).replace)
            }

        @Test
        fun `where TDLib does not allow it, nothing is offered`() =
            runViewModelTest {
                val viewModel = editingPhoto(canEditMedia = false)

                assertNull((viewModel.bar as ComposerBar.Edit).replace)
                viewModel.effects.test {
                    send(viewModel, ReplyEvent.ReplaceMedia)

                    assertEquals(ChatEffect.ActionUnavailable, awaitItem())
                }
            }

        @Test
        fun `the picked photo opens in a replacement preview, and Replace sends it with the edit's text`() =
            runViewModelTest {
                val viewModel = editingPhoto()
                type(viewModel, "Sunrise")

                send(viewModel, ReplyEvent.ReplacementPicked("content://media/9"))

                assertEquals(listOf("content://media/9" to AttachmentKind.Media), attachments.prepared)
                assertEquals(true, viewModel.state.attachPreview?.isReplacement)
                send(viewModel, ReplyEvent.SaveReplacement)

                val edit = attachments.editedMedia.single()
                assertEquals(
                    FakeAttachmentRepository.EditedMedia(
                        PRIVATE_CHAT,
                        1,
                        FakeAttachmentRepository.photo("content://media/9"),
                        text("Sunrise"),
                        showCaptionAboveMedia = false,
                    ),
                    edit,
                )
                assertNull(viewModel.state.attachPreview)
                assertNull(viewModel.bar)
                assertTrue(attachments.discarded.isEmpty())
            }

        @Test
        fun `a refused replacement brings the edit back with a snackbar`() =
            runViewModelTest {
                for (error in listOf(DomainError.FloodWait(30), DomainError.Network, DomainError.Unauthorized)) {
                    attachments.onEditMedia = { Outcome.Failure(error) }
                    val viewModel = editingPhoto()
                    send(viewModel, ReplyEvent.ReplacementPicked("content://media/9"))

                    viewModel.effects.test {
                        send(viewModel, ReplyEvent.SaveReplacement)

                        assertEquals(ChatEffect.ShowError(error), awaitItem())
                    }
                    assertEquals(1L, (viewModel.bar as? ComposerBar.Edit)?.messageId)
                    assertEquals(text("Sunset"), viewModel.composerText.content)
                }
            }

        @Test
        fun `an edit cancelled before Replace replaces nothing, and the copy is deleted`() =
            runViewModelTest {
                val viewModel = editingPhoto()
                send(viewModel, ReplyEvent.ReplacementPicked("content://media/9"), ChatEvent.CancelEdit)

                viewModel.effects.test {
                    send(viewModel, ReplyEvent.SaveReplacement)

                    assertEquals(ChatEffect.ActionUnavailable, awaitItem())
                }
                assertTrue(attachments.editedMedia.isEmpty())
                assertEquals(listOf(FakeAttachmentRepository.photo("content://media/9")), attachments.discarded)
            }

        @Test
        fun `the replacement preview survives process death`() =
            runViewModelTest {
                val before = editingPhoto()
                send(before, ReplyEvent.ReplacementPicked("content://media/9"))
                clear(before)

                val after =
                    privateChat(
                        mine = outgoing(1).copy(content = photo("Sunset")),
                        handle = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }),
                    )

                assertEquals(true, after.state.attachPreview?.isReplacement)
                assertEquals(ReplaceMedia.Media, (after.bar as? ComposerBar.Edit)?.replace)
            }
    }

    /** Collects the picker's sheet as the screen does, and gives its latest value. */
    private fun TestScope.backgroundCollect(viewModel: ChatViewModel): () -> ReplyChatsSheet? {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.replyChatsSheet.collect {}
        }
        runCurrent()
        return { viewModel.replyChatsSheet.value }
    }

    private companion object {
        /** Past the composer's draft delay. */
        const val DRAFT_DELAY = 1_001L
    }
}
