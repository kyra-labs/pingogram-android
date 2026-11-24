package dev.kyralabs.pingogram.feature.chats.ui.drafts

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.ME
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.draft
import dev.kyralabs.pingogram.core.testing.FakeChatLockRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeGroupRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.chats.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeWallClock
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
 * [DraftsViewModel] on a fake [ChatRepository] (`REQUIREMENTS.md` FR-X16): the drafts of both lists, newest first,
 * reply-only drafts left out, the rest of the lists loaded once the page collects them, loading and empty states,
 * drafts that change, and what a tap does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DraftsViewModelTest {
    private val chats = FakeChatRepository()
    private val users = FakeUserRepository().apply { myUserId.value = ME }
    private val chatLock = FakeChatLockRepository(locked = setOf(9L))

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A view model whose state is collected, as by the page. */
    private fun TestScope.viewModel(): DraftsViewModel {
        val viewModel =
            DraftsViewModel(
                chatRepository = chats,
                chatLock = chatLock,
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

    private val DraftsViewModel.chatIds
        get() = (uiState.value as DraftsUiState.Content).drafts.map { it.chatId }

    @Test
    fun `the drafts of both lists show newest first, without reply-only drafts`() =
        runTest {
            chats.setChats(
                ChatListType.Main,
                chat(2, draft = draft("older", NOW.minusSeconds(60))),
                chat(3),
                chat(4, draft = draft("")),
            )
            chats.setChats(ChatListType.Archive, chat(5, draft = draft("newest", NOW)))

            val viewModel = viewModel()

            assertEquals(listOf(5L, 2L), viewModel.chatIds)
            assertEquals(NO_MESSAGE, (viewModel.uiState.value as DraftsUiState.Content).drafts.first().messageId)
        }

    @Test
    fun `the rest of both lists loads once the page collects it`() =
        runTest {
            chats.answerLoads(Outcome.Success(true), Outcome.Success(true), Outcome.Success(false))

            viewModel()

            assertEquals(
                listOf(ChatListType.Main, ChatListType.Main, ChatListType.Main, ChatListType.Archive),
                chats.loadRequests.map { it.first },
            )
        }

    @Test
    fun `a page that fails ends loading its list`() =
        runTest {
            chats.answerLoads(Outcome.Failure(DomainError.Network))

            val viewModel = viewModel()

            assertEquals(listOf(ChatListType.Main, ChatListType.Archive), chats.loadRequests.map { it.first })
            assertEquals(DraftsUiState.Empty, viewModel.uiState.value)
        }

    @Test
    fun `without drafts it loads until both lists are loaded, then is empty`() =
        runTest {
            val page = CompletableDeferred<Outcome<Boolean>>()
            chats.onLoadChats = { _, _ -> page.await() }

            val viewModel = viewModel()
            assertEquals(DraftsUiState.Loading, viewModel.uiState.value)

            page.complete(Outcome.Success(false))
            runCurrent()

            assertEquals(DraftsUiState.Empty, viewModel.uiState.value)
        }

    @Test
    fun `a draft found while the lists load shows at once`() =
        runTest {
            chats.onLoadChats = { _, _ -> CompletableDeferred<Outcome<Boolean>>().await() }
            chats.setChats(ChatListType.Main, chat(2, draft = draft("hello")))

            assertEquals(listOf(2L), viewModel().chatIds)
        }

    @Test
    fun `a draft sent or cleared leaves the page`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(2, draft = draft("hello")))
            val viewModel = viewModel()

            chats.setChats(ChatListType.Main, chat(2))
            runCurrent()

            assertEquals(DraftsUiState.Empty, viewModel.uiState.value)
        }

    @Test
    fun `a locked chat's draft never shows`() =
        runTest {
            chats.setChats(ChatListType.Main, chat(2, draft = draft("hello")), chat(9, draft = draft("secret")))

            assertEquals(listOf(2L), viewModel().chatIds)
        }

    @Test
    fun `a chat in both lists shows once`() =
        runTest {
            val chat = chat(2, draft = draft("hello"))
            chats.setChats(ChatListType.Main, chat)
            chats.setChats(ChatListType.Archive, chat)

            assertEquals(listOf(2L), viewModel().chatIds)
        }

    @Test
    fun `a tap opens the chat`() =
        runTest {
            val viewModel = viewModel()
            val effects = mutableListOf<DraftsEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            viewModel.onEvent(DraftsEvent.Open(2))
            runCurrent()

            assertEquals(listOf<DraftsEffect>(DraftsEffect.OpenChat(2)), effects)
        }
}
