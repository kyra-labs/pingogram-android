package dev.kyralabs.pingogram.feature.chats.ui.mentions

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import dev.kyralabs.pingogram.core.domain.repository.MentionRepository
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.ME
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.message
import dev.kyralabs.pingogram.feature.chats.ui.FakeGroupRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.chats.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeWallClock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * [MentionsViewModel] on a fake [MentionRepository] (`REQUIREMENTS.md` FR-X1): the messages read once the page
 * collects them, unread ones apart, nothing found, errors and their retry, a quiet refresh, and what a tap does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MentionsViewModelTest {
    private val repository = FakeMentionRepository()
    private val users = FakeUserRepository().apply { myUserId.value = ME }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A view model whose state is collected, as by the page. */
    private fun TestScope.viewModel(): MentionsViewModel {
        val viewModel =
            MentionsViewModel(
                mentionRepository = repository,
                userRepository = users,
                groupRepository = FakeGroupRepository(),
                clock = FakeWallClock(NOW),
                timePatterns = FakeTimePatterns(),
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.send(
        viewModel: MentionsViewModel,
        event: MentionsEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    private fun found(
        chatId: Long,
        messageId: Long,
        isUnread: Boolean = false,
    ) = FoundMessage(
        message = message(chatId, id = messageId).copy(containsUnreadMention = isUnread),
        chat = chat(chatId, type = ChatType.BasicGroup(chatId)),
    )

    private val MentionsViewModel.content get() = uiState.value as MentionsUiState.Content

    @Test
    fun `the messages are read once the page collects them, unread ones apart`() =
        runTest {
            repository.answer = Outcome.Success(listOf(found(1, 11, isUnread = true), found(2, 21), found(1, 12)))
            val viewModel = viewModel()

            assertEquals(1, repository.calls)
            assertEquals(listOf(1L to 11L), viewModel.content.unread.map { it.chatId to it.messageId })
            assertEquals(listOf(2L to 21L, 1L to 12L), viewModel.content.earlier.map { it.chatId to it.messageId })
        }

    @Test
    fun `nothing found is the empty state`() =
        runTest {
            repository.answer = Outcome.Success(emptyList())

            assertEquals(MentionsUiState.Empty, viewModel().uiState.value)
        }

    @Test
    fun `an error shows, and a retry reads them again with the loading state meanwhile`() =
        runTest {
            repository.answer = Outcome.Failure(DomainError.Network)
            val viewModel = viewModel()
            assertEquals(MentionsUiState.Error(DomainError.Network), viewModel.uiState.value)

            val answer = CompletableDeferred<Outcome<List<FoundMessage>>>()
            repository.next = answer
            send(viewModel, MentionsEvent.Retry)
            assertEquals(MentionsUiState.Loading, viewModel.uiState.value)

            answer.complete(Outcome.Success(listOf(found(1, 11))))
            runCurrent()
            assertEquals(2, repository.calls)
            assertEquals(1, viewModel.content.earlier.size)
        }

    @Test
    fun `a retry without an error reads nothing`() =
        runTest {
            repository.answer = Outcome.Success(listOf(found(1, 11)))
            val viewModel = viewModel()

            send(viewModel, MentionsEvent.Retry)

            assertEquals(1, repository.calls)
        }

    @Test
    fun `a refresh keeps the rows until the new ones replace them`() =
        runTest {
            repository.answer = Outcome.Success(listOf(found(1, 11, isUnread = true)))
            val viewModel = viewModel()

            val answer = CompletableDeferred<Outcome<List<FoundMessage>>>()
            repository.next = answer
            send(viewModel, MentionsEvent.Refresh)
            assertEquals(1, viewModel.content.unread.size)

            answer.complete(Outcome.Success(listOf(found(1, 11))))
            runCurrent()
            assertEquals(0, viewModel.content.unread.size)
            assertEquals(1, viewModel.content.earlier.size)
        }

    @Test
    fun `a tap opens the chat at the message`() =
        runTest {
            val viewModel = viewModel()
            val effects = mutableListOf<MentionsEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            send(viewModel, MentionsEvent.Open(chatId = 1, messageId = 11))

            assertEquals(listOf(MentionsEffect.OpenMessage(1, 11)), effects)
        }

    /** Answers [answer], or once [next] when set, and counts the calls. */
    private class FakeMentionRepository : MentionRepository {
        var answer: Outcome<List<FoundMessage>> = Outcome.Success(emptyList())
        var next: CompletableDeferred<Outcome<List<FoundMessage>>>? = null
        var calls = 0

        override suspend fun mentions(): Outcome<ImmutableList<FoundMessage>> {
            calls++
            val waiting = next?.also { next = null }
            val result = waiting?.await() ?: answer
            return when (result) {
                is Outcome.Success -> Outcome.Success(result.value.toImmutableList())
                is Outcome.Failure -> result
            }
        }
    }
}
