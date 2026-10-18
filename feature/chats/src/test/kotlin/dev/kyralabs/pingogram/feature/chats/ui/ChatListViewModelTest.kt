package dev.kyralabs.pingogram.feature.chats.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatAction
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.ME
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.draft
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.folder
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.message
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.supergroup
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.user
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Locale
import dev.kyralabs.pingogram.core.ui.R as UiR

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {
    private val chats = FakeChatRepository()
    private val users = FakeUserRepository().apply { myUserId.value = ME }
    private val groups = FakeGroupRepository()
    private val actions = FakeChatActionRepository()
    private val connection = FakeConnectionRepository()
    private val clock = FakeWallClock(NOW)
    private val savedState = SavedStateHandle()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A view model whose state is collected, as by the screen. */
    private fun TestScope.viewModel(handle: SavedStateHandle = savedState): ChatListViewModel {
        val viewModel = createViewModel(handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.createViewModel(handle: SavedStateHandle = savedState) =
        ChatListViewModel(
            savedStateHandle = handle,
            chatRepository = chats,
            userRepository = users,
            groupRepository = groups,
            chatActionRepository = actions,
            connectionRepository = connection,
            clock = clock,
            dispatcher = StandardTestDispatcher(testScheduler),
            locale = { Locale.US },
        )

    private fun TestScope.send(
        viewModel: ChatListViewModel,
        vararg events: ChatListEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    /** A view model showing [chat] alone in the main list, and its row. */
    private fun TestScope.rowOf(chat: Chat): ChatListItemModel {
        chats.setChats(ChatListType.Main, chat)
        return viewModel().item(chat.id)
    }

    private val ChatListViewModel.state get() = uiState.value

    private val ChatListViewModel.items get() = (state.content as ChatListContent.Content).items

    private fun ChatListViewModel.item(id: Long) = items.single { it.id == id }

    private fun ChatListViewModel.ids() = items.map { it.id }

    private fun res(
        id: Int,
        vararg args: Any,
    ) = UiText.resource(id, *args)

    private val loadsChatsForever: suspend (ChatListType, Int) -> Outcome<Boolean> = { _, _ ->
        CompletableDeferred<Outcome<Boolean>>().await()
    }

    // Loading, content, empty

    @Test
    fun `shows progress until the first chats arrive`() =
        runTest {
            chats.onLoadChats = loadsChatsForever

            createViewModel().uiState.test {
                assertEquals(ChatListContent.Loading, awaitItem().content)

                chats.setChats(ChatListType.Main, chat(id = 2))

                assertEquals(listOf(2L), (awaitItem().content as ChatListContent.Content).items.map { it.id })
            }
        }

    @Test
    fun `shows the chats in the repository's order`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 3), chat(id = 2), chat(id = 4))

            assertEquals(listOf(3L, 2L, 4L), viewModel().ids())
        }

    @Test
    fun `a main list without chats is empty`() =
        runTest {
            assertEquals(ChatListContent.Empty(isFolder = false), viewModel().state.content)
        }

    @Test
    fun `a folder without chats is empty as a folder`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.SelectFolder(1))

            assertEquals(ChatListContent.Empty(isFolder = true), viewModel.state.content)
        }

    @Test
    fun `the connection status is shown`() =
        runTest {
            val viewModel = viewModel()

            connection.connectionStatus.value = ConnectionStatus.WaitingForNetwork
            runCurrent()

            assertEquals(ConnectionStatus.WaitingForNetwork, viewModel.state.connection)
        }

    // Errors

    @Test
    fun `a network failure without chats shows the error`() =
        runTest {
            chats.onLoadChats = { _, _ -> Outcome.Failure(DomainError.Network) }

            assertEquals(ChatListContent.Error(DomainError.Network), viewModel().state.content)
        }

    @Test
    fun `a flood wait without chats shows how long to wait`() =
        runTest {
            chats.onLoadChats = { _, _ -> Outcome.Failure(DomainError.FloodWait(42)) }

            assertEquals(ChatListContent.Error(DomainError.FloodWait(42)), viewModel().state.content)
        }

    @Test
    fun `retry loads the first page again, showing progress`() =
        runTest {
            chats.onLoadChats = { _, _ -> Outcome.Failure(DomainError.Network) }
            val viewModel = viewModel()
            chats.onLoadChats = loadsChatsForever

            send(viewModel, ChatListEvent.Retry)

            assertEquals(ChatListContent.Loading, viewModel.state.content)
            assertEquals(listOf(ChatListType.Main to 20, ChatListType.Main to 20), chats.loadRequests)
        }

    @Test
    fun `a successful retry shows the chats`() =
        runTest {
            chats.onLoadChats = { _, _ -> Outcome.Failure(DomainError.Network) }
            val viewModel = viewModel()
            chats.onLoadChats = { _, _ -> Outcome.Success(true) }

            send(viewModel, ChatListEvent.Retry)
            chats.setChats(ChatListType.Main, chat(id = 2))
            runCurrent()

            assertEquals(listOf(2L), viewModel.ids())
        }

    @Test
    fun `a failed page keeps the chats shown`() =
        runTest {
            chats.onLoadChats = { _, _ -> Outcome.Success(true) }
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()
            chats.onLoadChats = { _, _ -> Outcome.Failure(DomainError.Network) }

            send(viewModel, ChatListEvent.LoadMore)

            assertEquals(listOf(2L), viewModel.ids())
        }

    // Paging

    @Test
    fun `loads a first page of 20 chats of the main list`() =
        runTest {
            viewModel()

            assertEquals(listOf(ChatListType.Main to 20), chats.loadRequests)
        }

    @Test
    fun `nearing the end loads pages of the repository's page size`() =
        runTest {
            chats.onLoadChats = { _, _ -> Outcome.Success(true) }
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.LoadMore, ChatListEvent.LoadMore)

            val page = ChatRepository.PAGE_SIZE
            assertEquals(
                listOf(ChatListType.Main to 20, ChatListType.Main to page, ChatListType.Main to page),
                chats.loadRequests,
            )
        }

    @Test
    fun `stops loading once the list is complete`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.LoadMore, ChatListEvent.Retry)

            assertEquals(listOf(ChatListType.Main to 20), chats.loadRequests)
        }

    @Test
    fun `loads one page of a list at a time`() =
        runTest {
            chats.onLoadChats = loadsChatsForever
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.LoadMore, ChatListEvent.LoadMore, ChatListEvent.Retry)

            assertEquals(listOf(ChatListType.Main to 20), chats.loadRequests)
        }

    // Folders

    @Test
    fun `selecting a folder shows its chats and loads its first page`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1))
            chats.setChats(ChatListType.Main, chat(id = 2))
            chats.setChats(ChatListType.Folder(1), chat(id = 3, lists = listOf(ChatListType.Folder(1))))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.SelectFolder(1))

            assertEquals(1, viewModel.state.selectedFolderId)
            assertEquals(listOf(3L), viewModel.ids())
            assertEquals(listOf(ChatListType.Main to 20, ChatListType.Folder(1) to 20), chats.loadRequests)
        }

    @Test
    fun `selecting All chats returns to the main list without loading it again`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1))
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()

            send(viewModel, ChatListEvent.SelectFolder(1), ChatListEvent.SelectFolder(null))

            assertNull(viewModel.state.selectedFolderId)
            assertEquals(listOf(2L), viewModel.ids())
            assertEquals(listOf(ChatListType.Main to 20, ChatListType.Folder(1) to 20), chats.loadRequests)
        }

    @Test
    fun `the selected folder survives process death`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1))
            send(viewModel(), ChatListEvent.SelectFolder(1))

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertEquals(1, restored.state.selectedFolderId)
        }

    @Test
    fun `a deleted folder falls back to the main list`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1), folder(2))
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()
            send(viewModel, ChatListEvent.SelectFolder(1))

            chats.folderList.value = persistentListOf(folder(2))
            runCurrent()

            assertNull(viewModel.state.selectedFolderId)
            assertEquals(listOf(2L), viewModel.ids())
        }

    @Test
    fun `without folders there are no tabs`() =
        runTest {
            assertEquals(persistentListOf<FolderTab>(), viewModel().state.folderTabs)
        }

    @Test
    fun `All chats sits among the folders where TDLib puts it, each tab with its unread unmuted chats`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1, "Work"), folder(2, "Family"))
            chats.mainPosition.value = 1
            chats.unreadCounts.value =
                mapOf(
                    ChatListType.Main to UnreadChatCount(10, 6, 4, 0, 0),
                    ChatListType.Folder(1) to UnreadChatCount(5, 3, 2, 0, 0),
                )

            assertEquals(
                listOf(
                    FolderTab.Folder(1, "Work", unreadCount = 2),
                    FolderTab.AllChats(unreadCount = 4),
                    FolderTab.Folder(2, "Family", unreadCount = 0),
                ),
                viewModel().state.folderTabs,
            )
        }

    @Test
    fun `All chats goes last when TDLib puts it beyond the folders`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1))
            chats.mainPosition.value = 5

            assertEquals(FolderTab.AllChats(0), viewModel().state.folderTabs.last())
        }

    // Previews

    @Test
    fun `someone typing comes before the draft and the last message`() =
        runTest {
            actions.setActions(2, SenderAction(MessageSender.User(2), ChatAction.Typing))

            val row = rowOf(chat(id = 2, lastMessage = message(chatId = 2), draft = draft("plans")))

            assertEquals(ChatPreview.Action(res(UiR.string.action_typing)), row.preview)
        }

    @Test
    fun `a draft comes before the last message`() =
        runTest {
            val row = rowOf(chat(id = 2, lastMessage = message(chatId = 2), draft = draft("plans")))

            assertEquals(ChatPreview.Draft(UiText.Plain("plans")), row.preview)
        }

    @Test
    fun `the last message of a private chat shows without a sender`() =
        runTest {
            val row = rowOf(chat(id = 2, lastMessage = message(chatId = 2)))

            assertEquals(ChatPreview.Message(sender = null, text = UiText.Plain("Hello")), row.preview)
        }

    @Test
    fun `a chat without messages has no preview`() =
        runTest {
            assertEquals(ChatPreview.None, rowOf(chat(id = 2)).preview)
        }

    @Test
    fun `a group names who is typing`() =
        runTest {
            users.setUsers(user(5, firstName = "Alice"))
            actions.setActions(GROUP, SenderAction(MessageSender.User(5), ChatAction.Typing))

            val row = rowOf(chat(id = GROUP, type = BASIC_GROUP))

            assertEquals(ChatPreview.Action(res(UiR.string.named_action_typing, UiText.Plain("Alice"))), row.preview)
        }

    @Test
    fun `a group names two people typing, and counts more`() =
        runTest {
            users.setUsers(user(5, firstName = "Alice"), user(6, firstName = "Bob"), user(7, firstName = "Carol"))
            actions.setActions(
                GROUP,
                SenderAction(MessageSender.User(5), ChatAction.Typing),
                SenderAction(MessageSender.User(6), ChatAction.Typing),
            )
            actions.setActions(
                OTHER_GROUP,
                SenderAction(MessageSender.User(5), ChatAction.Typing),
                SenderAction(MessageSender.User(6), ChatAction.Typing),
                SenderAction(MessageSender.User(7), ChatAction.Typing),
            )
            chats.setChats(
                ChatListType.Main,
                chat(id = GROUP, type = BASIC_GROUP),
                chat(id = OTHER_GROUP, type = ChatType.Supergroup(9, isChannel = false)),
            )

            val viewModel = viewModel()

            assertEquals(
                ChatPreview.Action(res(UiR.string.typing_two, UiText.Plain("Alice"), UiText.Plain("Bob"))),
                viewModel.item(GROUP).preview,
            )
            assertEquals(
                ChatPreview.Action(UiText.plural(UiR.plurals.typing_many, 3, 3)),
                viewModel.item(OTHER_GROUP).preview,
            )
        }

    @Test
    fun `people doing different things show the first of them`() =
        runTest {
            users.setUsers(user(5, firstName = "Alice"), user(6, firstName = "Bob"))
            actions.setActions(
                GROUP,
                SenderAction(MessageSender.User(6), ChatAction.RecordingVoiceNote),
                SenderAction(MessageSender.User(5), ChatAction.Typing),
            )

            val row = rowOf(chat(id = GROUP, type = BASIC_GROUP))

            assertEquals(
                ChatPreview.Action(res(UiR.string.named_action_recording_voice, UiText.Plain("Bob"))),
                row.preview,
            )
        }

    @Test
    fun `messages in groups show their sender's first name, or You`() =
        runTest {
            users.setUsers(user(5, firstName = "Alice", lastName = "Smith"))
            chats.setChats(
                ChatListType.Main,
                chat(id = GROUP, type = BASIC_GROUP, lastMessage = message(GROUP, sender = MessageSender.User(5))),
                chat(
                    id = OTHER_GROUP,
                    type = ChatType.Supergroup(9, isChannel = false),
                    lastMessage = message(OTHER_GROUP, sender = MessageSender.User(ME), isOutgoing = true),
                ),
            )

            val viewModel = viewModel()

            assertEquals(UiText.Plain("Alice"), (viewModel.item(GROUP).preview as ChatPreview.Message).sender)
            assertEquals(
                res(UiR.string.preview_sender_you),
                (viewModel.item(OTHER_GROUP).preview as ChatPreview.Message).sender,
            )
        }

    @Test
    fun `channel posts, anonymous admins and service messages show no sender`() =
        runTest {
            users.setUsers(user(5, firstName = "Alice"))
            val channel = ChatType.Supergroup(9, isChannel = true)
            chats.setChats(
                ChatListType.Main,
                chat(
                    id = 20,
                    type = channel,
                    lastMessage = message(20, sender = MessageSender.Chat(20), isChannelPost = true),
                ),
                chat(id = 21, type = BASIC_GROUP, lastMessage = message(21, sender = MessageSender.Chat(21))),
                chat(
                    id = 22,
                    type = BASIC_GROUP,
                    lastMessage =
                        message(
                            22,
                            sender = MessageSender.User(5),
                            content = MessageContent.Service(ServiceAction.MemberJoined),
                        ),
                ),
            )

            val viewModel = viewModel()

            assertNull((viewModel.item(20).preview as ChatPreview.Message).sender)
            assertNull((viewModel.item(21).preview as ChatPreview.Message).sender)
            assertEquals(
                ChatPreview.Message(sender = null, text = res(UiR.string.preview_joined, UiText.Plain("Alice"))),
                viewModel.item(22).preview,
            )
        }

    @Test
    fun `a changed sender name maps the row again`() =
        runTest {
            users.setUsers(user(5, firstName = "Alice"))
            chats.setChats(
                ChatListType.Main,
                chat(id = GROUP, type = BASIC_GROUP, lastMessage = message(GROUP, sender = MessageSender.User(5))),
            )
            val viewModel = viewModel()

            users.setUsers(user(5, firstName = "Alicia"))
            runCurrent()

            assertEquals(UiText.Plain("Alicia"), (viewModel.item(GROUP).preview as ChatPreview.Message).sender)
        }

    // Special chats

    @Test
    fun `Saved Messages has its own name and icon, and nobody online or typing`() =
        runTest {
            users.setUsers(user(ME, status = UserStatus.Online(NOW.epochSecond + 300)))
            actions.setActions(ME, SenderAction(MessageSender.User(ME), ChatAction.Typing))

            val lastMessage = message(ME, sender = MessageSender.User(ME), isOutgoing = true)

            val row = rowOf(chat(id = ME, title = "Me", lastMessage = lastMessage))

            assertEquals(res(UiR.string.chat_saved_messages), row.title)
            assertEquals(UiR.drawable.ic_bookmark_filled, row.placeholderIcon)
            assertFalse(row.isOnline)
            assertNull(row.outgoingStatus)
            assertTrue(row.preview is ChatPreview.Message)
        }

    @Test
    fun `a deleted account has its own name and a person icon`() =
        runTest {
            users.setUsers(user(2, firstName = "", type = UserType.Deleted))

            val row = rowOf(chat(id = 2, title = ""))

            assertEquals(res(UiR.string.user_deleted_account), row.title)
            assertEquals(UiR.drawable.ic_person_filled, row.placeholderIcon)
        }

    @Test
    fun `other chats keep their title and no icon`() =
        runTest {
            val row = rowOf(chat(id = 2, title = "Ada"))

            assertEquals(UiText.Plain("Ada"), row.title)
            assertNull(row.placeholderIcon)
        }

    // Online

    @Test
    fun `a user is online until the status expires, checked every minute`() =
        runTest {
            users.setUsers(user(2, status = UserStatus.Online(expiresAtEpochSeconds = NOW.epochSecond + 60)))
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()
            assertTrue(viewModel.item(2).isOnline)

            clock.setTime(Instant.parse("2026-10-18T09:42:00Z"))
            runCurrent()
            assertTrue(viewModel.item(2).isOnline)

            clock.setTime(Instant.parse("2026-10-18T09:43:00Z"))
            runCurrent()
            assertFalse(viewModel.item(2).isOnline)
        }

    @Test
    fun `bots and offline users are not online`() =
        runTest {
            val online = UserStatus.Online(NOW.epochSecond + 300)
            users.setUsers(
                user(2, status = online, type = UserType.Bot),
                user(3, status = UserStatus.Offline(NOW.epochSecond - 60)),
            )
            chats.setChats(ChatListType.Main, chat(id = 2), chat(id = 3))

            val viewModel = viewModel()

            assertFalse(viewModel.item(2).isOnline)
            assertFalse(viewModel.item(3).isOnline)
        }

    // Badges and flags

    @Test
    fun `badges come from the user or the supergroup, warnings first`() =
        runTest {
            users.setUsers(user(2, isVerified = true), user(3, isFake = true))
            groups.supergroupMap.value =
                persistentMapOf(
                    9L to supergroup(9, isVerified = true, isScam = true),
                    10L to supergroup(10, isChannel = true, isVerified = true),
                )
            chats.setChats(
                ChatListType.Main,
                chat(id = 2),
                chat(id = 3),
                chat(id = 20, type = ChatType.Supergroup(9, isChannel = false)),
                chat(id = 21, type = ChatType.Supergroup(10, isChannel = true)),
                chat(id = 22, type = BASIC_GROUP),
            )

            val viewModel = viewModel()

            assertEquals(
                listOf(TitleBadge.Verified, TitleBadge.Fake, TitleBadge.Scam, TitleBadge.Verified, null),
                viewModel.items.map { it.badge },
            )
        }

    @Test
    fun `a secret chat shows its user and a lock`() =
        runTest {
            users.setUsers(user(2, isVerified = true, status = UserStatus.Online(NOW.epochSecond + 300)))

            val row = rowOf(chat(id = 30, type = ChatType.Secret(secretChatId = 1, userId = 2)))

            assertTrue(row.isSecret)
            assertTrue(row.isOnline)
            assertEquals(TitleBadge.Verified, row.badge)
        }

    @Test
    fun `unread counts, mentions, reactions, the unread mark, mute and pins are shown`() =
        runTest {
            val row =
                rowOf(
                    chat(
                        id = 2,
                        unreadCount = 3,
                        unreadMentionCount = 1,
                        unreadReactionCount = 2,
                        isMarkedAsUnread = true,
                        isMuted = true,
                        pinnedIn = listOf(ChatListType.Main),
                    ),
                )

            assertEquals(3, row.unreadCount)
            assertTrue(row.hasUnreadMention)
            assertTrue(row.hasUnreadReactions)
            assertTrue(row.isMarkedAsUnread)
            assertTrue(row.isMuted)
            assertTrue(row.isPinned)
        }

    @Test
    fun `a chat is pinned only in the lists it is pinned in`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1))
            val pinnedInFolder =
                chat(
                    id = 2,
                    lists = listOf(ChatListType.Main, ChatListType.Folder(1)),
                    pinnedIn = listOf(ChatListType.Folder(1)),
                )
            chats.setChats(ChatListType.Main, pinnedInFolder)
            chats.setChats(ChatListType.Folder(1), pinnedInFolder)
            val viewModel = viewModel()
            assertFalse(viewModel.item(2).isPinned)

            send(viewModel, ChatListEvent.SelectFolder(1))

            assertTrue(viewModel.item(2).isPinned)
        }

    // Outgoing messages

    @Test
    fun `the current user's last message shows whether it is pending, failed, sent or read`() =
        runTest {
            val failed = SendingState.Failed(DomainError.Network, canRetry = true, retryAfter = null)
            chats.setChats(
                ChatListType.Main,
                chat(id = 2, lastMessage = outgoing(2, sendingState = SendingState.Pending)),
                chat(id = 3, lastMessage = outgoing(3, sendingState = failed)),
                chat(id = 4, lastMessage = outgoing(4), lastReadOutboxMessageId = 9),
                chat(id = 5, lastMessage = outgoing(5), lastReadOutboxMessageId = 10),
                chat(id = 6, lastMessage = message(6)),
            )

            val viewModel = viewModel()

            assertEquals(
                listOf(OutgoingStatus.Pending, OutgoingStatus.Failed, OutgoingStatus.Sent, OutgoingStatus.Read, null),
                viewModel.items.map { it.outgoingStatus },
            )
        }

    @Test
    fun `channel posts show no status`() =
        runTest {
            val channel = ChatType.Supergroup(9, isChannel = true)

            val row = rowOf(chat(id = 20, type = channel, lastMessage = outgoing(20, sender = MessageSender.Chat(20))))

            assertNull(row.outgoingStatus)
        }

    // Times

    @Test
    fun `times show the time today, the weekday this week, the day this year, else the date`() =
        runTest {
            chats.setChats(
                ChatListType.Main,
                chat(id = 2, lastMessage = message(2, date = Instant.parse("2026-10-18T08:05:00Z"))),
                chat(id = 3, lastMessage = message(3, date = Instant.parse("2026-10-17T23:00:00Z"))),
                chat(id = 4, lastMessage = message(4, date = Instant.parse("2026-10-12T10:00:00Z"))),
                chat(id = 5, lastMessage = message(5, date = Instant.parse("2026-10-11T10:00:00Z"))),
                chat(id = 6, lastMessage = message(6, date = Instant.parse("2025-03-17T10:00:00Z"))),
                chat(id = 7),
            )

            val viewModel = viewModel()

            assertEquals(
                listOf("8:05 AM", "Sat", "Mon", "Oct 11", "3/17/25", ""),
                viewModel.items.map { it.time.replace(' ', ' ') },
            )
        }

    @Test
    fun `a newer draft shows its own time and hides the message status`() =
        runTest {
            val row =
                rowOf(
                    chat(
                        id = 2,
                        lastMessage = outgoing(2, date = Instant.parse("2026-10-12T10:00:00Z")),
                        draft = draft("plans", date = Instant.parse("2026-10-18T08:05:00Z")),
                    ),
                )

            assertEquals("8:05 AM", row.time.replace(' ', ' '))
            assertNull(row.outgoingStatus)
        }

    // Performance

    @Test
    fun `rows whose chat did not change are reused when another user changes`() =
        runTest {
            users.setUsers(user(2), user(3))
            chats.setChats(ChatListType.Main, chat(id = 2), chat(id = 3))
            val viewModel = viewModel()
            val before = viewModel.items

            users.setUsers(user(2), user(3, status = UserStatus.Online(NOW.epochSecond + 300)))
            runCurrent()

            assertSame(before[0], viewModel.items[0])
            assertNotSame(before[1], viewModel.items[1])
            assertTrue(viewModel.items[1].isOnline)
        }

    private fun outgoing(
        chatId: Long,
        sendingState: SendingState? = null,
        sender: MessageSender = MessageSender.User(ME),
        date: Instant = NOW,
    ) = message(chatId, id = 10, sender = sender, isOutgoing = true, sendingState = sendingState, date = date)

    private companion object {
        const val GROUP = 50L
        const val OTHER_GROUP = 51L
        val BASIC_GROUP = ChatType.BasicGroup(basicGroupId = 8)
    }
}
