package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.ME
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.user
import dev.kyralabs.pingogram.feature.chats.ui.FakeSearchRepository.Companion.found
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * [NewChatViewModel] on a [FakeSearchRepository] (`REQUIREMENTS.md` FR-L7): the contacts read once the screen collects
 * them, their rows, no contacts, each error and its retry, and what tapping does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewChatViewModelTest {
    private val search = FakeSearchRepository()
    private val users = FakeUserRepository().apply { myUserId.value = ME }
    private val groups = FakeGroupRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel() =
        NewChatViewModel(
            searchRepository = search,
            userRepository = users,
            groupRepository = groups,
            clock = FakeWallClock(NOW),
            timePatterns = FakeTimePatterns(),
            dispatcher = StandardTestDispatcher(testScheduler),
            locale = { Locale.US },
        )

    /** A view model whose state is collected, as by the screen. */
    private fun TestScope.viewModel(): NewChatViewModel {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.send(
        viewModel: NewChatViewModel,
        event: NewChatEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    /** The effects [viewModel] sends from now on, collected as the screen does. */
    private fun TestScope.effectsOf(viewModel: NewChatViewModel): List<NewChatEffect> {
        val effects = mutableListOf<NewChatEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private val NewChatViewModel.contacts get() = (uiState.value as NewChatUiState.Content).contacts

    // Loading

    @Test
    fun `the contacts are read once the screen collects them, not before`() =
        runTest {
            val viewModel = createViewModel()
            runCurrent()

            assertEquals(NewChatUiState.Loading, viewModel.uiState.value)
            assertTrue(search.calls.isEmpty())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            runCurrent()
            assertEquals(listOf("allContacts"), search.calls)
        }

    @Test
    fun `it is loading while the contacts are on their way`() =
        runTest {
            val answer = CompletableDeferred<Outcome<ImmutableList<Chat>>>()
            search.onContacts = { answer.await() }
            val viewModel = viewModel()

            assertEquals(NewChatUiState.Loading, viewModel.uiState.value)
            answer.complete(found(chat(2)))
            runCurrent()
            assertEquals(listOf(2L), viewModel.contacts.map { it.id })
        }

    // Content

    @Test
    fun `contacts show in the repository's order, with name, badge, online dot and username`() =
        runTest {
            users.setUsers(
                user(2, firstName = "Ada", isVerified = true, status = UserStatus.Online(NOW.epochSecond + 60))
                    .copy(usernames = persistentListOf("ada")),
                user(4, firstName = "Grace"),
            )
            search.onContacts = { found(chat(2, title = "Ada Lovelace"), chat(4, title = "Grace Hopper")) }

            val contacts = viewModel().contacts

            assertEquals(listOf(2L, 4L), contacts.map { it.id })
            val ada = contacts.first()
            assertEquals(UiText.Plain("Ada Lovelace"), ada.title)
            assertEquals(TitleBadge.Verified, ada.badge)
            assertTrue(ada.isOnline)
            assertEquals(UiText.resource(R.string.search_username, "ada"), ada.subtitle)
            assertNull(contacts.last().subtitle)
        }

    @Test
    fun `rows follow the users as TDLib updates them`() =
        runTest {
            users.setUsers(user(2))
            search.onContacts = { found(chat(2)) }
            val viewModel = viewModel()

            users.setUsers(user(2, status = UserStatus.Online(NOW.epochSecond + 60)))
            runCurrent()

            assertTrue(viewModel.contacts.single().isOnline)
            assertEquals(listOf("allContacts"), search.calls)
        }

    @Test
    fun `without contacts it is empty`() =
        runTest {
            assertEquals(NewChatUiState.Empty, viewModel().uiState.value)
        }

    // Errors

    @Test
    fun `a flood wait shows its error`() =
        runTest {
            search.onContacts = { Outcome.Failure(DomainError.FloodWait(7)) }

            assertEquals(NewChatUiState.Error(DomainError.FloodWait(7)), viewModel().uiState.value)
        }

    @Test
    fun `no connection shows its error`() =
        runTest {
            search.onContacts = { Outcome.Failure(DomainError.Network) }

            assertEquals(NewChatUiState.Error(DomainError.Network), viewModel().uiState.value)
        }

    @Test
    fun `a logged-out session shows its error`() =
        runTest {
            search.onContacts = { Outcome.Failure(DomainError.Unauthorized) }

            assertEquals(NewChatUiState.Error(DomainError.Unauthorized), viewModel().uiState.value)
        }

    @Test
    fun `retry reads the contacts again, loading meanwhile`() =
        runTest {
            search.onContacts = { Outcome.Failure(DomainError.Network) }
            val viewModel = viewModel()
            val answer = CompletableDeferred<Outcome<ImmutableList<Chat>>>()
            search.onContacts = { answer.await() }

            send(viewModel, NewChatEvent.Retry)

            assertEquals(NewChatUiState.Loading, viewModel.uiState.value)
            answer.complete(found(chat(2)))
            runCurrent()
            assertEquals(listOf(2L), viewModel.contacts.map { it.id })
            assertEquals(listOf("allContacts", "allContacts"), search.calls)
        }

    @Test
    fun `nothing is retried by itself, nor without a failure`() =
        runTest {
            search.onContacts = { Outcome.Failure(DomainError.Network) }
            val viewModel = viewModel()
            search.onContacts = { found(chat(2)) }
            runCurrent()

            assertEquals(listOf("allContacts"), search.calls)
            send(viewModel, NewChatEvent.Retry)
            send(viewModel, NewChatEvent.Retry)
            assertEquals(listOf("allContacts", "allContacts"), search.calls)
        }

    // Taps

    @Test
    fun `a contact opens its private chat`() =
        runTest {
            search.onContacts = { found(chat(2)) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, NewChatEvent.OpenChat(2))

            assertEquals(listOf(NewChatEffect.OpenChat(2)), effects)
        }

    @Test
    fun `new group and new channel open their flows`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, NewChatEvent.Action(NewChatAction.Group))
            send(viewModel, NewChatEvent.Action(NewChatAction.Channel))

            assertEquals(listOf(NewChatEffect.NewGroup, NewChatEffect.NewChannel), effects)
        }

    @Test
    fun `new contact opens Add contact`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, NewChatEvent.Action(NewChatAction.Contact))

            assertEquals(listOf(NewChatEffect.AddContact), effects)
        }
}
