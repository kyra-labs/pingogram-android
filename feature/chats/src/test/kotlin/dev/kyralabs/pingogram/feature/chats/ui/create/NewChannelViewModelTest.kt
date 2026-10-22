package dev.kyralabs.pingogram.feature.chats.ui.create

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatCreationLimits
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.domain.model.UsernameProblem
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.user
import dev.kyralabs.pingogram.feature.chats.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeWallClock
import dev.kyralabs.pingogram.feature.chats.ui.create.FakeChatCreationRepository.Companion.CHANNEL_ID
import dev.kyralabs.pingogram.feature.chats.ui.create.FakeChatCreationRepository.Companion.LINK
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * [NewChannelViewModel] (`REQUIREMENTS.md` FR-P4) on fakes: the details that create the channel, private with its
 * invite link or public with a username checked as it is typed, adding subscribers or skipping that, the people
 * Telegram did not add, each error path, and restoring after process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewChannelViewModelTest {
    private val contacts = FakeContactRepository()
    private val creation = FakeChatCreationRepository()
    private val users = FakeUserRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val ada = user(2, firstName = "Ada", lastName = "Lovelace").copy(isContact = true)
    private val alan = user(3, firstName = "Alan", lastName = "Turing").copy(isContact = true)

    /** A view model whose steps are collected, as by the screens. */
    private fun TestScope.viewModel(savedState: SavedStateHandle = SavedStateHandle()): NewChannelViewModel {
        val viewModel =
            NewChannelViewModel(
                savedStateHandle = savedState,
                repository = creation,
                userRepository = users,
                contactRepository = contacts,
                clock = FakeWallClock(NOW),
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.details.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.members.collect {} }
        runCurrent()
        return viewModel
    }

    private class Effects {
        val details = mutableListOf<CreateChatEffect>()
        val type = mutableListOf<CreateChatEffect>()
        val members = mutableListOf<CreateChatEffect>()
    }

    private fun TestScope.effectsOf(viewModel: NewChannelViewModel): Effects {
        val effects = Effects()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        backgroundScope.launch(dispatcher) { viewModel.detailsEffects.collect(effects.details::add) }
        backgroundScope.launch(dispatcher) { viewModel.typeEffects.collect(effects.type::add) }
        backgroundScope.launch(dispatcher) { viewModel.memberEffects.collect(effects.members::add) }
        return effects
    }

    private fun TestScope.details(
        viewModel: NewChannelViewModel,
        event: ChatDetailsEvent,
    ) {
        viewModel.onDetailsEvent(event)
        runCurrent()
    }

    private fun TestScope.type(
        viewModel: NewChannelViewModel,
        event: NewChannelTypeEvent,
    ) {
        viewModel.onTypeEvent(event)
        runCurrent()
    }

    /** A view model past its first step: the channel exists. */
    private fun TestScope.created(): NewChannelViewModel {
        val viewModel = viewModel()
        details(viewModel, ChatDetailsEvent.TitleChanged("News"))
        details(viewModel, ChatDetailsEvent.Create)
        return viewModel
    }

    // Details

    @Test
    fun `the details start empty, and next without a name shows the field's error`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(NewChannelDetailsUiState(), viewModel.details.value)

            details(viewModel, ChatDetailsEvent.Create)

            assertTrue(viewModel.details.value.titleError)
            assertTrue(creation.channels.isEmpty())
        }

    @Test
    fun `next creates the channel once with its name, description and photo, then reads its link`() =
        runTest {
            val answer = CompletableDeferred<Outcome<Long>>()
            creation.onCreateChannel = { answer.await() }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            details(viewModel, ChatDetailsEvent.TitleChanged("News"))
            details(viewModel, ChatDetailsEvent.DescriptionChanged("What's new"))
            details(viewModel, ChatDetailsEvent.PhotoPicked("p"))

            details(viewModel, ChatDetailsEvent.Create)
            details(viewModel, ChatDetailsEvent.Create)
            assertTrue(viewModel.details.value.isCreating)
            answer.complete(Outcome.Success(CHANNEL_ID))
            runCurrent()

            assertEquals(listOf(ChannelRequest("News", "What's new", "/cache/uploads/p.jpg")), creation.channels)
            assertEquals(listOf(CreateChatEffect.Next), effects.details)
            assertEquals(InviteLinkState.Ready(LINK), viewModel.type.value.inviteLink)
            // The channel exists: its details stay as they were sent.
            details(viewModel, ChatDetailsEvent.TitleChanged("Other"))
            assertEquals("News", viewModel.details.value.title)
            clear(viewModel)
            assertTrue(creation.discarded.isEmpty())
        }

    @Test
    fun `a description is cut at Telegram's limit`() =
        runTest {
            val viewModel = viewModel()

            details(
                viewModel,
                ChatDetailsEvent.DescriptionChanged(
                    "y".repeat(ChatCreationLimits.DESCRIPTION_LENGTH_MAX + 1),
                ),
            )

            assertEquals(ChatCreationLimits.DESCRIPTION_LENGTH_MAX, viewModel.details.value.description.length)
        }

    @Test
    fun `creating fails with a countdown, at the name, or as a snackbar`() =
        runTest {
            creation.onCreateChannel = { Outcome.Failure(DomainError.FloodWait(2)) }
            val waiting = viewModel()
            details(waiting, ChatDetailsEvent.TitleChanged("News"))
            details(waiting, ChatDetailsEvent.Create)
            assertEquals(FloodWait(2, 2), waiting.details.value.floodWait)
            advanceUntilIdle()
            assertNull(waiting.details.value.floodWait)

            creation.onCreateChannel = { Outcome.Failure(DomainError.TitleInvalid) }
            details(waiting, ChatDetailsEvent.Create)
            assertTrue(waiting.details.value.titleError)

            for (error in COMMON_ERRORS + DomainError.TooManyChats + DomainError.AccountRestricted) {
                creation.onCreateChannel = { Outcome.Failure(error) }
                val viewModel = viewModel()
                val effects = effectsOf(viewModel)
                details(viewModel, ChatDetailsEvent.TitleChanged("News"))
                details(viewModel, ChatDetailsEvent.Create)
                assertEquals(listOf(CreateChatEffect.ShowError(error)), effects.details)
            }
        }

    // Type

    @Test
    fun `a private channel goes on with its link, and a link that failed can be read again`() =
        runTest {
            creation.onInviteLink = { Outcome.Failure(DomainError.Network) }
            val viewModel = created()
            val effects = effectsOf(viewModel)
            assertEquals(InviteLinkState.Error(DomainError.Network), viewModel.type.value.inviteLink)

            creation.onInviteLink = { Outcome.Success(LINK) }
            type(viewModel, NewChannelTypeEvent.RetryLink)
            assertEquals(InviteLinkState.Ready(LINK), viewModel.type.value.inviteLink)
            assertFalse(viewModel.type.value.isPublic)

            type(viewModel, NewChannelTypeEvent.Continue)
            assertEquals(listOf(CreateChatEffect.Next), effects.type)
            assertTrue(creation.setUsernames.isEmpty())
        }

    @Test
    fun `a username that cannot be one says why at once, without asking Telegram`() =
        runTest {
            val viewModel = created()
            type(viewModel, NewChannelTypeEvent.TypeChanged(isPublic = true))

            type(viewModel, NewChannelTypeEvent.UsernameChanged("@png"))
            advanceUntilIdle()

            assertEquals("png", viewModel.type.value.username)
            assertEquals(UsernameStatus.Problem(UsernameProblem.TooShort), viewModel.type.value.usernameStatus)
            assertTrue(creation.checkedUsernames.isEmpty())
            assertFalse(viewModel.type.value.canContinue)
        }

    @Test
    fun `a username is checked with Telegram after a pause in typing, the latest only`() =
        runTest {
            val viewModel = created()
            type(viewModel, NewChannelTypeEvent.TypeChanged(isPublic = true))

            type(viewModel, NewChannelTypeEvent.UsernameChanged("pingogram"))
            assertEquals(UsernameStatus.Checking, viewModel.type.value.usernameStatus)
            advanceTimeBy(NewChannelViewModel.USERNAME_CHECK_DELAY.inWholeMilliseconds / 2)
            type(viewModel, NewChannelTypeEvent.UsernameChanged("pingogram_news"))
            advanceUntilIdle()

            assertEquals(listOf(CHANNEL_ID to "pingogram_news"), creation.checkedUsernames)
            assertEquals(UsernameStatus.Checked(UsernameCheck.Available), viewModel.type.value.usernameStatus)
            assertTrue(viewModel.type.value.canContinue)
        }

    @Test
    fun `next sets a public channel's username, then goes on`() =
        runTest {
            val viewModel = created()
            val effects = effectsOf(viewModel)
            type(viewModel, NewChannelTypeEvent.TypeChanged(isPublic = true))
            type(viewModel, NewChannelTypeEvent.UsernameChanged("pingogram_news"))
            advanceUntilIdle()

            type(viewModel, NewChannelTypeEvent.Continue)

            assertEquals(listOf(CHANNEL_ID to "pingogram_news"), creation.setUsernames)
            assertEquals(listOf(CreateChatEffect.Next), effects.type)
        }

    @Test
    fun `a username Telegram refuses shows at its field, a flood wait counts down, others are snackbars`() =
        runTest {
            val viewModel = created()
            val effects = effectsOf(viewModel)
            type(viewModel, NewChannelTypeEvent.TypeChanged(isPublic = true))
            type(viewModel, NewChannelTypeEvent.UsernameChanged("pingogram_news"))
            advanceUntilIdle()

            creation.onSetUsername = { Outcome.Failure(DomainError.UsernameTaken) }
            type(viewModel, NewChannelTypeEvent.Continue)
            assertEquals(UsernameStatus.Checked(UsernameCheck.Taken), viewModel.type.value.usernameStatus)
            assertTrue(effects.type.isEmpty())

            type(viewModel, NewChannelTypeEvent.UsernameChanged("pingogram_news2"))
            advanceUntilIdle()
            creation.onSetUsername = { Outcome.Failure(DomainError.FloodWait(5)) }
            type(viewModel, NewChannelTypeEvent.Continue)
            assertEquals(FloodWait(5, 5), viewModel.type.value.floodWait)
            assertFalse(viewModel.type.value.canContinue)
            advanceUntilIdle()

            creation.onSetUsername = { Outcome.Failure(DomainError.Network) }
            type(viewModel, NewChannelTypeEvent.Continue)
            assertEquals(listOf(CreateChatEffect.ShowError(DomainError.Network)), effects.type)
        }

    @Test
    fun `back on a later step opens the channel`() =
        runTest {
            val viewModel = created()
            val effects = effectsOf(viewModel)

            viewModel.onFinish()
            runCurrent()

            assertEquals(listOf(CreateChatEffect.OpenChat(CHANNEL_ID)), effects.type)
        }

    // Subscribers

    @Test
    fun `skipping opens the channel without adding anyone`() =
        runTest {
            val viewModel = created()
            val effects = effectsOf(viewModel)

            viewModel.onAddMembers()
            runCurrent()

            assertTrue(creation.added.isEmpty())
            assertEquals(listOf(CreateChatEffect.OpenChat(CHANNEL_ID)), effects.members)
        }

    @Test
    fun `the people picked are added, then the channel opens`() =
        runTest {
            contacts.setContacts(ada, alan)
            val viewModel = created()
            val effects = effectsOf(viewModel)
            viewModel.onMembersEvent(MemberPickerEvent.Toggle(3))
            viewModel.onMembersEvent(MemberPickerEvent.Toggle(2))

            viewModel.onAddMembers()
            runCurrent()

            assertEquals(listOf(CHANNEL_ID to listOf(3L, 2L)), creation.added)
            assertEquals(listOf(CreateChatEffect.OpenChat(CHANNEL_ID)), effects.members)
        }

    @Test
    fun `the people Telegram did not add are named, and their link can be shared`() =
        runTest {
            contacts.setContacts(ada, alan)
            users.setUsers(alan)
            creation.onAddMembers = { Outcome.Success(persistentListOf(3)) }
            val viewModel = created()
            val effects = effectsOf(viewModel)
            viewModel.onMembersEvent(MemberPickerEvent.Toggle(3))

            viewModel.onAddMembers()
            runCurrent()
            assertEquals(
                NotAddedDialog(CHANNEL_ID, 1, persistentListOf("Alan Turing")),
                viewModel.memberState.value.notAdded,
            )

            viewModel.onNotAddedEvent(NotAddedEvent.ShareLink)
            runCurrent()
            assertEquals(
                listOf(CreateChatEffect.ShareLink(LINK), CreateChatEffect.OpenChat(CHANNEL_ID)),
                effects.members,
            )
        }

    @Test
    fun `adding fails with a countdown or a snackbar, and can be tried again`() =
        runTest {
            contacts.setContacts(ada)
            val viewModel = created()
            val effects = effectsOf(viewModel)
            viewModel.onMembersEvent(MemberPickerEvent.Toggle(2))

            creation.onAddMembers = { Outcome.Failure(DomainError.FloodWait(2)) }
            viewModel.onAddMembers()
            runCurrent()
            assertEquals(FloodWait(2, 2), viewModel.memberState.value.floodWait)
            viewModel.onAddMembers()
            runCurrent()
            assertEquals(1, creation.added.size)
            advanceUntilIdle()

            creation.onAddMembers = { Outcome.Failure(DomainError.Unauthorized) }
            viewModel.onAddMembers()
            runCurrent()
            assertEquals(listOf(CreateChatEffect.ShowError(DomainError.Unauthorized)), effects.members)
            assertFalse(viewModel.memberState.value.isAdding)
        }

    // Process death

    @Test
    fun `the channel, its type and its username survive process death, asked about again`() =
        runTest {
            val savedState = SavedStateHandle()
            val viewModel = viewModel(savedState)
            details(viewModel, ChatDetailsEvent.TitleChanged("News"))
            details(viewModel, ChatDetailsEvent.Create)
            type(viewModel, NewChannelTypeEvent.TypeChanged(isPublic = true))
            type(viewModel, NewChannelTypeEvent.UsernameChanged("pingogram_news"))
            advanceUntilIdle()
            val checks = creation.checkedUsernames.size

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))
            advanceUntilIdle()

            assertEquals("News", restored.details.value.title)
            assertTrue(restored.type.value.isPublic)
            assertEquals("pingogram_news", restored.type.value.username)
            assertEquals(UsernameStatus.Checked(UsernameCheck.Available), restored.type.value.usernameStatus)
            assertEquals(checks + 1, creation.checkedUsernames.size)
            assertEquals(InviteLinkState.Ready(LINK), restored.type.value.inviteLink)
            // It exists: back opens it, nothing creates it again.
            details(restored, ChatDetailsEvent.Create)
            assertEquals(1, creation.channels.size)
        }
}
