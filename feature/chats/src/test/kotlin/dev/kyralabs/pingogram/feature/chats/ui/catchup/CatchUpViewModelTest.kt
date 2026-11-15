package dev.kyralabs.pingogram.feature.chats.ui.catchup

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.testing.FakeChatListSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeChatLockRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.ui.ChatCall
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.message
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.text
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.user
import dev.kyralabs.pingogram.feature.chats.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeGroupRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.chats.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeWallClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

/** Catch-up's round ([CatchUpViewModel]): the order, marking as read, skipping, the done state, the settings. */
@OptIn(ExperimentalCoroutinesApi::class)
class CatchUpViewModelTest {
    private val chats = FakeChatRepository()
    private val messages = FakeMessageRepository()
    private val users = FakeUserRepository()
    private val settings = FakeChatListSettingsRepository()
    private val chatLock = FakeChatLockRepository()
    private val clock = FakeWallClock(NOW)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(handle: SavedStateHandle = SavedStateHandle()): CatchUpViewModel {
        val viewModel =
            CatchUpViewModel(
                savedStateHandle = handle,
                catchUpChats = CatchUpChats(chats, settings, chatLock),
                sources = CatchUpSources(chats, messages, users, FakeGroupRepository()),
                clock = clock,
                cardMapper = CatchUpCardMapper(clock, FakeTimePatterns(), locale = { Locale.US }),
                dispatcher = StandardTestDispatcher(testScheduler),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: CatchUpViewModel): List<CatchUpEffect> {
        val effects = mutableListOf<CatchUpEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun unread(
        id: Long,
        mentions: Int = 0,
        at: Long = id,
        muted: Boolean = false,
        type: ChatType = ChatType.Private(userId = id),
    ): Chat =
        chat(
            id = id,
            type = type,
            unreadCount = 3,
            unreadMentionCount = mentions,
            isMuted = muted,
            lastMessage = message(chatId = id, id = at, date = NOW.plusSeconds(at)),
        )

    private val CatchUpViewModel.cards get() = uiState.value.stage as CatchUpStage.Cards

    @Test
    fun `goes through the unread chats, mentions first, then the most recent`() =
        runTest {
            chats.setChats(ChatListType.Main, unread(2, at = 10), unread(3, at = 30), unread(4, mentions = 1, at = 5))

            val viewModel = createViewModel()

            assertEquals(4L, viewModel.cards.current.chatId)
            assertEquals(1, viewModel.cards.position)
            assertEquals(3, viewModel.cards.total)
            assertEquals(3L, viewModel.cards.next?.chatId)
            assertEquals(listOf(4L, 3L), messages.loadedNewest)
        }

    @Test
    fun `leaves muted chats, channels, read and locked chats out until muted ones are included`() =
        runTest {
            chats.setChats(
                ChatListType.Main,
                unread(2),
                unread(3, muted = true),
                unread(4, type = ChatType.Supergroup(supergroupId = 4, isChannel = true)),
                chat(id = 5),
                unread(6),
            )
            chatLock.lock(setOf(6L))

            val viewModel = createViewModel()
            assertEquals(1, viewModel.cards.total)

            viewModel.onEvent(CatchUpEvent.SetIncludesMuted(true))
            runCurrent()

            assertTrue(settings.settings.value.catchUpIncludesMuted)
            assertEquals(3, viewModel.cards.total)
            assertTrue(viewModel.uiState.value.includesMuted)
        }

    @Test
    fun `mark as read reads only the shown chat, as the chat list does, and moves on`() =
        runTest {
            chats.setChats(ChatListType.Main, unread(2, at = 20), unread(3, at = 10))
            val viewModel = createViewModel()

            viewModel.onEvent(CatchUpEvent.MarkAsRead)
            runCurrent()

            assertEquals(listOf<ChatCall>(ChatCall.MarkAsRead(2)), chats.calls)
            assertEquals(3L, viewModel.cards.current.chatId)
            assertEquals(2, viewModel.cards.position)
        }

    @Test
    fun `a chat read elsewhere counts as read, without Catch-up reading anything`() =
        runTest {
            chats.setChats(ChatListType.Main, unread(2, at = 20), unread(3, at = 10))
            val viewModel = createViewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(CatchUpEvent.OpenChat)
            runCurrent()
            assertEquals(listOf<CatchUpEffect>(CatchUpEffect.OpenChat(2)), effects)
            assertEquals(2L, viewModel.cards.current.chatId)

            chats.setChats(ChatListType.Main, chat(id = 2), unread(3, at = 10))
            runCurrent()

            assertEquals(3L, viewModel.cards.current.chatId)
            assertTrue(chats.calls.isEmpty())
        }

    @Test
    fun `skipped chats wait for review, and the done state counts the round`() =
        runTest {
            chats.setChats(ChatListType.Main, unread(2, at = 20), unread(3, at = 10))
            val viewModel = createViewModel()

            viewModel.onEvent(CatchUpEvent.Skip)
            viewModel.onEvent(CatchUpEvent.MarkAsRead)
            clock.setTime(NOW.plusSeconds(150))
            runCurrent()

            assertEquals(
                CatchUpStage.Done(chats = 2, minutes = 3, read = 1, replied = 0, skipped = 1),
                viewModel.uiState.value.stage,
            )

            viewModel.onEvent(CatchUpEvent.ReviewSkipped)
            runCurrent()

            assertEquals(2L, viewModel.cards.current.chatId)
            assertEquals(1, viewModel.cards.total)
        }

    @Test
    fun `nothing unread shows the empty state`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 2))

            assertEquals(CatchUpStage.Empty, createViewModel().uiState.value.stage)
        }

    @Test
    fun `a card shows the newest unread messages, oldest first, with their senders in groups`() =
        runTest {
            val group = ChatType.BasicGroup(basicGroupId = 7)
            chats.setChats(
                ChatListType.Main,
                chat(id = 7, type = group, unreadCount = 6, unreadMentionCount = 1, title = "Trip"),
            )
            users.setUsers(user(id = 20, firstName = "Meera"))
            messages.setHistory(
                7,
                *(16L downTo 10L)
                    .map { id ->
                        message(
                            chatId = 7,
                            id = id,
                            sender = MessageSender.User(20),
                            content = MessageContent.Text(text("Message $id"), linkPreview = null),
                        )
                    }.toTypedArray(),
            )

            val card = createViewModel().cards.current

            assertEquals(listOf(13L, 14L, 15L, 16L), card.messages?.map { it.id })
            assertEquals(UiText.Plain("Meera"), card.messages?.first()?.sender)
            assertEquals(3, card.earlierCount)
            assertEquals(1, card.mentionCount)
        }

    @Test
    fun `earlier messages show on request`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(id = 7, unreadCount = 6))
            messages.setHistory(7, *(16L downTo 10L).map { message(chatId = 7, id = it) }.toTypedArray())
            val viewModel = createViewModel()

            viewModel.onEvent(CatchUpEvent.ShowEarlier)
            runCurrent()

            assertEquals(
                7,
                viewModel.cards.current.messages
                    ?.size,
            )
            assertEquals(0, viewModel.cards.current.earlierCount)
        }

    @Test
    fun `the round survives process death`() =
        runTest {
            chats.setChats(ChatListType.Main, unread(2, at = 30), unread(3, at = 20), unread(4, at = 10))
            val handle = SavedStateHandle()
            createViewModel(handle).onEvent(CatchUpEvent.Skip)
            runCurrent()

            // A chat that arrives meanwhile does not join the round.
            chats.setChats(ChatListType.Main, unread(2, at = 30), unread(3, at = 20), unread(4, at = 10), unread(5))
            val restored = createViewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))

            assertEquals(3L, restored.cards.current.chatId)
            assertEquals(2, restored.cards.position)
            assertEquals(3, restored.cards.total)
        }

    @Test
    fun `a failed mark as read says why`() =
        runTest {
            chats.setChats(ChatListType.Main, unread(2))
            chats.onCall = { Outcome.Failure(DomainError.Network) }
            val viewModel = createViewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(CatchUpEvent.MarkAsRead)
            runCurrent()

            assertEquals(listOf<CatchUpEffect>(CatchUpEffect.ShowError(DomainError.Network)), effects)
        }

    @Test
    fun `the settings sheet opens and closes`() =
        runTest {
            chats.setChats(ChatListType.Main, unread(2))
            val viewModel = createViewModel()

            viewModel.onEvent(CatchUpEvent.ShowSettings(true))
            runCurrent()
            assertTrue(viewModel.uiState.value.showsSettings)

            viewModel.onEvent(CatchUpEvent.ShowSettings(false))
            runCurrent()
            assertEquals(false, viewModel.uiState.value.showsSettings)
        }

    @Test
    fun `replying answers the mention, then marks the chat as read and counts it as replied`() =
        runTest {
            val group = ChatType.BasicGroup(basicGroupId = 7)
            chats.setChats(
                ChatListType.Main,
                chat(id = 7, type = group, unreadCount = 3, unreadMentionCount = 1),
            )
            messages.setHistory(
                7,
                message(chatId = 7, id = 12),
                message(chatId = 7, id = 11).copy(containsUnreadMention = true),
                message(chatId = 7, id = 10),
            )
            val viewModel = createViewModel()
            assertEquals(
                11L,
                viewModel.cards.current.replyTo
                    ?.id,
            )

            viewModel.onEvent(CatchUpEvent.Reply)
            runCurrent()
            assertEquals(CatchUpReplyState(), viewModel.cards.reply)

            viewModel.onEvent(CatchUpEvent.SendReply(" On it "))
            runCurrent()

            assertEquals(listOf(Triple(7L, "On it", ReplyTarget(messageId = 11))), messages.sent)
            assertEquals(listOf<ChatCall>(ChatCall.MarkAsRead(7)), chats.calls)
            assertEquals(
                CatchUpStage.Done(chats = 1, minutes = 1, read = 0, replied = 1, skipped = 0),
                viewModel.uiState.value.stage,
            )
        }

    @Test
    fun `a reply Telegram refuses keeps the field open and says why`() =
        runTest {
            chats.setChats(ChatListType.Main, unread(2))
            messages.setHistory(2, message(chatId = 2, id = 5))
            messages.sendOutcome = Outcome.Failure(DomainError.Network)
            val viewModel = createViewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(CatchUpEvent.Reply)
            viewModel.onEvent(CatchUpEvent.SendReply("Hi"))
            runCurrent()

            assertEquals(listOf<CatchUpEffect>(CatchUpEffect.ShowError(DomainError.Network)), effects)
            assertEquals(CatchUpReplyState(), viewModel.cards.reply)
            assertTrue(chats.calls.isEmpty())
        }

    @Test
    fun `cancelling closes the reply field, which survives process death until then`() =
        runTest {
            chats.setChats(ChatListType.Main, unread(2))
            val handle = SavedStateHandle()
            createViewModel(handle).onEvent(CatchUpEvent.Reply)
            runCurrent()

            val restored = createViewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
            assertEquals(CatchUpReplyState(), restored.cards.reply)

            restored.onEvent(CatchUpEvent.CancelReply)
            runCurrent()
            assertEquals(null, restored.cards.reply)
        }
}
