package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatMemberStatus
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.BASIC_GROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GRACE
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.basicGroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.supergroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.forward.ChatForwarding
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardSheet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Forwarding from the conversation (`REQUIREMENTS.md` FR-C6): the Forward action only where TDLib allows it, the
 * forward sheet's chats (Saved Messages first, only chats the user can write in), the debounced search, picking chats,
 * the options, sending with the comment, the snackbars after it, and failures.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatForwardTest : ChatViewModelHarness() {
    private val saved = chat(id = ME, title = "Me")
    private val ada = chat(title = "Ada Lovelace")
    private val group = groupChat
    private val readOnlyChannel =
        chat(id = READ_ONLY_CHANNEL, type = ChatType.Supergroup(READ_ONLY_SUPERGROUP, isChannel = true), title = "News")
    private val ownChannel =
        chat(id = OWN_CHANNEL, type = ChatType.Supergroup(OWN_SUPERGROUP, isChannel = true), title = "Mine")
    private val secret =
        chat(id = SECRET_CHAT, type = ChatType.Secret(secretChatId = 1, userId = GRACE), title = "Grace")
    private val leftGroup = chat(id = LEFT_GROUP, type = ChatType.BasicGroup(LEFT_BASIC_GROUP), title = "Old group")

    private val photo =
        MessageContent.Photo(
            photo = Photo(sizes = persistentListOf(), minithumbnail = null),
            caption = text("Sunset"),
            hasSpoiler = false,
            isSecret = false,
            showCaptionAboveMedia = false,
        )

    /**
     * The conversation with Ada showing her text message 1 and her album of photos 2 and 3 (one with a caption); the
     * main chat list holds every kind of chat, Saved Messages last; the forward sheet is collected, as by the screen.
     */
    private fun TestScope.conversation(knowsSavedMessages: Boolean = true): ChatViewModel {
        users.setUsers(user(ME), user(ADA, firstName = "Ada"), user(GRACE, firstName = "Grace"))
        val all =
            listOf(ada, group, readOnlyChannel, ownChannel, secret, leftGroup) +
                listOfNotNull(saved.takeIf { knowsSavedMessages })
        chats.setChats(*all.toTypedArray())
        chats.mainList.value = all
        groups.basicGroupMap.value =
            mapOf(
                BASIC_GROUP_ID to basicGroup(memberCount = 3),
                LEFT_BASIC_GROUP to
                    basicGroup(memberCount = 3, status = ChatMemberStatus.Left).copy(id = LEFT_BASIC_GROUP),
            )
        groups.supergroupMap.value =
            mapOf(
                READ_ONLY_SUPERGROUP to supergroup(memberCount = 100).copy(id = READ_ONLY_SUPERGROUP),
                OWN_SUPERGROUP to
                    supergroup(memberCount = 10, status = ChatMemberStatus.Administrator(canPostMessages = true))
                        .copy(id = OWN_SUPERGROUP),
            )
        messages.setHistory(
            PRIVATE_CHAT,
            history(
                message(1),
                message(2, content = photo, mediaAlbumId = ALBUM),
                message(3, content = photo.copy(caption = text("")), mediaAlbumId = ALBUM),
            ),
        )
        val viewModel = viewModel(PRIVATE_CHAT)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.forwardSheet.collect {} }
        runCurrent()
        return viewModel
    }

    private val ChatViewModel.sheet: ForwardSheet? get() = forwardSheet.value

    private val ChatViewModel.targetIds: List<Long>? get() = sheet?.targets?.map { it.chatId }

    @Nested
    inner class Opening {
        @Test
        fun `the actions sheet offers forwarding where TDLib allows it`() =
            runViewModelTest {
                val viewModel = conversation()

                send(viewModel, ChatEvent.ShowMessageActions(1))

                assertTrue(MessageAction.Forward in viewModel.state.messageActions!!.actions)
            }

        @Test
        fun `protected content is not offered for forwarding`() =
            runViewModelTest {
                messageActions.onProperties = {
                    Outcome.Success(
                        FakeMessageActionRepository.ALL_ALLOWED.copy(canBeForwarded = false, canBeSaved = false),
                    )
                }
                val viewModel = conversation()

                send(viewModel, ChatEvent.ShowMessageActions(1))

                assertFalse(MessageAction.Forward in viewModel.state.messageActions!!.actions)
            }

        @Test
        fun `forwarding opens the sheet for the message`() =
            runViewModelTest {
                val viewModel = conversation()

                send(viewModel, ChatEvent.Forward(1))

                assertEquals(1, viewModel.sheet?.messageCount)
                assertNull(viewModel.state.messageActions)
            }

        @Test
        fun `an album is forwarded whole, and offers hiding its captions`() =
            runViewModelTest {
                val viewModel = conversation()

                send(viewModel, ChatEvent.Forward(3), ChatEvent.ToggleForwardTarget(ME), ChatEvent.SendForward)

                assertEquals(listOf("forward:$PRIVATE_CHAT:[2, 3]:$ME:false:false"), messageActions.calls)
            }

        @Test
        fun `captions and copies are offered only where the messages allow them`() =
            runViewModelTest {
                val viewModel = conversation()

                send(viewModel, ChatEvent.Forward(3))
                assertEquals(true to true, viewModel.sheet?.let { it.canHideSenderName to it.hasCaptions })

                send(viewModel, ChatEvent.DismissForward, ChatEvent.Forward(1))
                assertEquals(true to false, viewModel.sheet?.let { it.canHideSenderName to it.hasCaptions })

                messageActions.onProperties = {
                    Outcome.Success(FakeMessageActionRepository.ALL_ALLOWED.copy(canBeCopied = false))
                }
                send(viewModel, ChatEvent.DismissForward, ChatEvent.Forward(1))
                assertEquals(false, viewModel.sheet?.canHideSenderName)
            }

        @Test
        fun `a message TDLib no longer lets be forwarded says so`() =
            runViewModelTest {
                val viewModel = conversation()
                messageActions.onProperties = {
                    Outcome.Success(FakeMessageActionRepository.ALL_ALLOWED.copy(canBeForwarded = false))
                }

                viewModel.effects.test {
                    send(viewModel, ChatEvent.Forward(1))

                    assertEquals(ChatEffect.ActionUnavailable, awaitItem())
                }
                assertNull(viewModel.sheet)
            }

        @Test
        fun `properties TDLib cannot give are a snackbar`() =
            runViewModelTest {
                val viewModel = conversation()
                send(viewModel, ChatEvent.ShowMessageActions(1))
                messageActions.onProperties = { Outcome.Failure(DomainError.Network) }

                viewModel.effects.test {
                    send(viewModel, ChatEvent.Forward(1))

                    assertEquals(ChatEffect.ShowError(DomainError.Network), awaitItem())
                }
                assertNull(viewModel.sheet)
            }

        @Test
        fun `dismissing closes the sheet without forwarding`() =
            runViewModelTest {
                val viewModel = conversation()
                send(viewModel, ChatEvent.Forward(1), ChatEvent.ToggleForwardTarget(ME))

                send(viewModel, ChatEvent.DismissForward)

                assertNull(viewModel.sheet)
                assertEquals(emptyList<String>(), messageActions.calls)
            }

        @Test
        fun `opening again starts over`() =
            runViewModelTest {
                val viewModel = conversation()
                send(viewModel, ChatEvent.Forward(3), ChatEvent.ToggleForwardTarget(ME))
                send(viewModel, ChatEvent.SetForwardHideCaptions(true))
                viewModel.forwardComment.state.edit { append("Look") }

                send(viewModel, ChatEvent.DismissForward, ChatEvent.Forward(3))

                assertEquals(emptySet<Long>(), viewModel.sheet?.selectedIds)
                assertFalse(viewModel.sheet!!.hideCaptions)
                assertTrue(viewModel.forwardComment.isEmpty)
            }
    }

    @Nested
    inner class Targets {
        @Test
        fun `Saved Messages come first, then the chats the user can write in, in the list's order`() =
            runViewModelTest {
                val viewModel = conversation()

                send(viewModel, ChatEvent.Forward(1))

                // Not the channel the user only reads, nor the group they left.
                assertEquals(listOf(ME, PRIVATE_CHAT, GROUP_CHAT, OWN_CHANNEL, SECRET_CHAT), viewModel.targetIds)
                assertEquals(
                    UiText.resource(R.string.chat_saved_messages),
                    viewModel.sheet!!
                        .targets
                        .first()
                        .title,
                )
                assertEquals(
                    R.drawable.ic_bookmark_filled,
                    viewModel.sheet!!
                        .targets
                        .first()
                        .placeholderIcon,
                )
                assertTrue(
                    viewModel.sheet!!
                        .targets
                        .last()
                        .isSecret,
                )
            }

        @Test
        fun `secret chats are offered only for messages that can be copied into one`() =
            runViewModelTest {
                messageActions.onProperties = {
                    Outcome.Success(FakeMessageActionRepository.ALL_ALLOWED.copy(canBeCopiedToSecretChat = false))
                }
                val viewModel = conversation()

                send(viewModel, ChatEvent.Forward(1))

                assertFalse(SECRET_CHAT in viewModel.targetIds!!)
            }

        @Test
        fun `Saved Messages TDLib never had are created on the device`() =
            runViewModelTest {
                val viewModel = conversation(knowsSavedMessages = false)

                send(viewModel, ChatEvent.Forward(1))
                assertEquals(listOf(ME), chats.privateChatRequests)
                assertEquals(PRIVATE_CHAT, viewModel.targetIds?.first())

                // TDLib announces the chat it created.
                chats.setChats(*(chats.chats.value.values + saved).toTypedArray())
                runCurrent()

                assertEquals(ME, viewModel.targetIds?.first())
            }

        @Test
        fun `the end of the list loads the next page, one at a time, until there is no more`() =
            runViewModelTest {
                val viewModel = conversation()
                var more = true
                chats.onLoadChats = { Outcome.Success(more) }
                send(viewModel, ChatEvent.Forward(1))

                send(viewModel, ChatEvent.LoadMoreForwardTargets)
                more = false
                send(viewModel, ChatEvent.LoadMoreForwardTargets, ChatEvent.LoadMoreForwardTargets)

                assertEquals(2, chats.loadChatsCalls)
            }
    }

    @Nested
    inner class Search {
        @Test
        fun `a query is searched once it stays unchanged, showing only chats the user can write in`() =
            runViewModelTest {
                search.onSearchChats = { flowOf(Outcome.Success(persistentListOf(readOnlyChannel, group))) }
                val viewModel = conversation()
                send(viewModel, ChatEvent.Forward(1))

                send(viewModel, ChatEvent.ForwardQueryChanged("G"), ChatEvent.ForwardQueryChanged("Gr"))
                advanceTimeBy(ChatForwarding.DEBOUNCE_MILLIS - 1)
                runCurrent()
                assertEquals(emptyList<String>(), search.chatQueries)

                advanceTimeBy(2)
                runCurrent()

                assertEquals(listOf("Gr"), search.chatQueries)
                assertEquals(listOf(GROUP_CHAT), viewModel.targetIds)
                assertTrue(viewModel.sheet!!.isSearching)
            }

        @Test
        fun `results load, and the list comes back once the query is cleared`() =
            runViewModelTest {
                val results = MutableSharedFlow<Outcome<kotlinx.collections.immutable.ImmutableList<Chat>>>()
                search.onSearchChats = { results }
                val viewModel = conversation()
                send(viewModel, ChatEvent.Forward(1), ChatEvent.ForwardQueryChanged("Ada"))
                advanceTimeBy(ChatForwarding.DEBOUNCE_MILLIS + 1)
                runCurrent()
                assertTrue(viewModel.sheet!!.isLoading)

                results.emit(Outcome.Success(listOf(ada).toImmutableList()))
                runCurrent()
                assertEquals(listOf(PRIVATE_CHAT), viewModel.targetIds)

                send(viewModel, ChatEvent.ForwardQueryChanged(""))

                assertFalse(viewModel.sheet!!.isSearching)
                assertEquals(ME, viewModel.targetIds?.first())
            }

        @Test
        fun `a failed search shows why in place of the results`() =
            runViewModelTest {
                search.onSearchChats = { flowOf(Outcome.Failure(DomainError.FloodWait(5))) }
                val viewModel = conversation()
                send(viewModel, ChatEvent.Forward(1), ChatEvent.ForwardQueryChanged("Ada"))

                advanceTimeBy(ChatForwarding.DEBOUNCE_MILLIS + 1)
                runCurrent()

                assertEquals(DomainError.FloodWait(5), viewModel.sheet?.searchError)
                assertEquals(emptyList<Long>(), viewModel.targetIds)
            }

        @Test
        fun `picked chats stay picked while a search hides them`() =
            runViewModelTest {
                search.onSearchChats = { flowOf(Outcome.Success(persistentListOf(group))) }
                val viewModel = conversation()
                send(viewModel, ChatEvent.Forward(1), ChatEvent.ToggleForwardTarget(ME))

                send(viewModel, ChatEvent.ForwardQueryChanged("Gr"))
                advanceTimeBy(ChatForwarding.DEBOUNCE_MILLIS + 1)
                runCurrent()

                assertEquals(setOf(ME), viewModel.sheet?.selectedIds)
            }
    }

    @Nested
    inner class Picking {
        @Test
        fun `a tap picks a chat, another unpicks it`() =
            runViewModelTest {
                val viewModel = conversation()
                send(viewModel, ChatEvent.Forward(1))

                send(viewModel, ChatEvent.ToggleForwardTarget(GROUP_CHAT), ChatEvent.ToggleForwardTarget(ME))
                assertEquals(listOf(GROUP_CHAT, ME), viewModel.sheet?.selectedIds?.toList())

                send(viewModel, ChatEvent.ToggleForwardTarget(GROUP_CHAT))
                assertEquals(setOf(ME), viewModel.sheet?.selectedIds)
            }

        @Test
        fun `hiding captions hides the sender's name, showing the name shows the captions`() =
            runViewModelTest {
                val viewModel = conversation()
                send(viewModel, ChatEvent.Forward(3))

                send(viewModel, ChatEvent.SetForwardHideCaptions(true))
                assertEquals(true to true, viewModel.sheet!!.let { it.hideSenderName to it.hideCaptions })

                send(viewModel, ChatEvent.SetForwardHideSenderName(false))
                assertEquals(false to false, viewModel.sheet!!.let { it.hideSenderName to it.hideCaptions })

                send(viewModel, ChatEvent.SetForwardHideSenderName(true))
                assertEquals(true to false, viewModel.sheet!!.let { it.hideSenderName to it.hideCaptions })
            }
    }

    @Nested
    inner class Sending {
        @Test
        fun `sending closes the sheet and forwards to each picked chat with the comment and the options`() =
            runViewModelTest {
                val viewModel = conversation()
                send(viewModel, ChatEvent.Forward(2))
                send(viewModel, ChatEvent.ToggleForwardTarget(GROUP_CHAT), ChatEvent.ToggleForwardTarget(ME))
                send(viewModel, ChatEvent.SetForwardHideCaptions(true))
                viewModel.forwardComment.state.edit { append("Look at this") }

                send(viewModel, ChatEvent.SendForward)

                assertNull(viewModel.sheet)
                assertEquals(
                    listOf(
                        "forward:$PRIVATE_CHAT:[2, 3]:$GROUP_CHAT:true:true",
                        "forward:$PRIVATE_CHAT:[2, 3]:$ME:true:true",
                    ),
                    messageActions.calls,
                )
                assertEquals(listOf("Look at this", "Look at this"), messageActions.forwardComments.map { it?.text })
            }

        @Test
        fun `a blank comment is not sent`() =
            runViewModelTest {
                val viewModel = conversation()
                send(viewModel, ChatEvent.Forward(1), ChatEvent.ToggleForwardTarget(ME))
                viewModel.forwardComment.state.edit { append("  ") }

                send(viewModel, ChatEvent.SendForward)

                assertEquals(listOf(null), messageActions.forwardComments)
            }

        @Test
        fun `nothing is sent while no chat is picked`() =
            runViewModelTest {
                val viewModel = conversation()
                send(viewModel, ChatEvent.Forward(1))

                send(viewModel, ChatEvent.SendForward)

                assertEquals(emptyList<String>(), messageActions.calls)
                assertEquals(1, viewModel.sheet?.messageCount)
            }

        @Test
        fun `one other chat is named, and can be opened from the snackbar`() =
            runViewModelTest {
                val viewModel = conversation()
                send(viewModel, ChatEvent.Forward(1), ChatEvent.ToggleForwardTarget(GROUP_CHAT))

                viewModel.effects.test {
                    send(viewModel, ChatEvent.SendForward)

                    assertEquals(ChatEffect.Forwarded(1, UiText.Plain("Group"), openChatId = GROUP_CHAT), awaitItem())
                }
            }

        @Test
        fun `this chat is not opened again`() =
            runViewModelTest {
                val viewModel = conversation()
                send(viewModel, ChatEvent.Forward(1), ChatEvent.ToggleForwardTarget(PRIVATE_CHAT))

                viewModel.effects.test {
                    send(viewModel, ChatEvent.SendForward)

                    assertEquals(ChatEffect.Forwarded(1, UiText.Plain("Ada Lovelace"), openChatId = null), awaitItem())
                }
            }

        @Test
        fun `several chats are counted`() =
            runViewModelTest {
                val viewModel = conversation()
                send(viewModel, ChatEvent.Forward(1))
                send(viewModel, ChatEvent.ToggleForwardTarget(GROUP_CHAT), ChatEvent.ToggleForwardTarget(ME))

                viewModel.effects.test {
                    send(viewModel, ChatEvent.SendForward)

                    assertEquals(ChatEffect.Forwarded(2), awaitItem())
                }
            }

        @Test
        fun `a chat that refuses the messages is reported with the others that got them`() =
            runViewModelTest {
                messageActions.onAction = { call ->
                    if (call.contains(
                            ":$GROUP_CHAT:",
                        )
                    ) {
                        Outcome.Failure(DomainError.SendingNotAllowed)
                    } else {
                        Outcome.Success(Unit)
                    }
                }
                val viewModel = conversation()
                send(viewModel, ChatEvent.Forward(1))
                send(viewModel, ChatEvent.ToggleForwardTarget(GROUP_CHAT), ChatEvent.ToggleForwardTarget(ME))

                viewModel.effects.test {
                    send(viewModel, ChatEvent.SendForward)

                    assertEquals(
                        ChatEffect.ForwardFailed(DomainError.SendingNotAllowed, failed = 1, forwarded = 1),
                        awaitItem(),
                    )
                }
                assertEquals(2, messageActions.calls.size)
            }

        @Test
        fun `a flood wait stops forwarding to the chats left`() =
            runViewModelTest {
                messageActions.onAction = { Outcome.Failure(DomainError.FloodWait(30)) }
                val viewModel = conversation()
                send(viewModel, ChatEvent.Forward(1))
                send(viewModel, ChatEvent.ToggleForwardTarget(GROUP_CHAT), ChatEvent.ToggleForwardTarget(ME))

                viewModel.effects.test {
                    send(viewModel, ChatEvent.SendForward)

                    assertEquals(
                        ChatEffect.ForwardFailed(DomainError.FloodWait(30), failed = 2, forwarded = 0),
                        awaitItem(),
                    )
                }
                assertEquals(1, messageActions.calls.size)
            }
    }

    private companion object {
        const val READ_ONLY_CHANNEL = -1_000_000_000_300L
        const val READ_ONLY_SUPERGROUP = 300L
        const val OWN_CHANNEL = -1_000_000_000_400L
        const val OWN_SUPERGROUP = 400L
        const val SECRET_CHAT = -5_000L
        const val LEFT_GROUP = -500L
        const val LEFT_BASIC_GROUP = 50L
        const val ALBUM = 7L
    }
}
