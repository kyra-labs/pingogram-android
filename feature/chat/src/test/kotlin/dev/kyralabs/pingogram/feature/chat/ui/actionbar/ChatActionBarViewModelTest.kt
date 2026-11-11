package dev.kyralabs.pingogram.feature.chat.ui.actionbar

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AddContactResult
import dev.kyralabs.pingogram.core.domain.model.BlockedSendersPage
import dev.kyralabs.pingogram.core.domain.model.ChatActionBar
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.EditableContact
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.BlockListRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatActionBarRepository
import dev.kyralabs.pingogram.core.domain.repository.ContactRepository
import dev.kyralabs.pingogram.feature.chat.ui.ActionBarEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures
import dev.kyralabs.pingogram.feature.chat.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeUserRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The action bar of a chat someone new started ([ChatActionBarViewModel], `docs/DESIGN_SYSTEM.md` §6). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatActionBarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val chats = FakeChatRepository()
    private val users = FakeUserRepository()
    private val contacts = FakeContacts()
    private val blockList = FakeBlockList()
    private val actionBars = FakeActionBars()
    private val savedState = SavedStateHandle()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        users.setUsers(ChatFixtures.user(USER, firstName = "Ada", lastName = "Lovelace"))
        bar(ChatActionBar.ReportAddBlock(canUnarchive = false))
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun bar(bar: ChatActionBar?) =
        chats.setChats(ChatFixtures.chat(id = CHAT, type = ChatType.Private(USER)).copy(actionBar = bar))

    private fun TestScope.viewModel(): ChatActionBarViewModel {
        val viewModel = ChatActionBarViewModel(CHAT, savedState, chats, users, contacts, blockList, actionBars)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.bar.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: ChatActionBarViewModel): List<ActionBarEffect> =
        mutableListOf<ActionBarEffect>().also { list ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.collect(list::add) }
        }

    @Test
    fun `the bar names the other person`() =
        runTest(dispatcher) {
            assertEquals(ActionBarUi(ChatActionBar.ReportAddBlock(canUnarchive = false), "Ada"), viewModel().bar.value)
        }

    @Test
    fun `no bar shows nothing, and the bar leaves as TDLib removes it`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            bar(null)
            runCurrent()

            assertNull(viewModel.bar.value)
        }

    @Test
    fun `blocking asks first, then blocks and dismisses the bar`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(ActionBarEvent.Block)
            assertTrue(viewModel.blockDialog.value)
            assertTrue(blockList.blocked.isEmpty())

            viewModel.onEvent(ActionBarEvent.ConfirmBlock)
            runCurrent()

            assertFalse(viewModel.blockDialog.value)
            assertEquals(listOf<MessageSender>(MessageSender.User(USER)), blockList.blocked)
            assertEquals(listOf(CHAT), actionBars.dismissed)
            assertEquals(listOf<ActionBarEffect>(ActionBarEffect.Blocked), effects)
        }

    @Test
    fun `the question survives process death`() =
        runTest(dispatcher) {
            viewModel().onEvent(ActionBarEvent.Block)

            assertTrue(viewModel().blockDialog.value)
        }

    @Test
    fun `a refused block keeps the bar and says why`() =
        runTest(dispatcher) {
            blockList.result = Outcome.Failure(DomainError.Network)
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(ActionBarEvent.ConfirmBlock)
            runCurrent()

            assertTrue(actionBars.dismissed.isEmpty())
            assertEquals(listOf<ActionBarEffect>(ActionBarEffect.Failed(DomainError.Network)), effects)
        }

    @Test
    fun `adding a contact keeps their name and does not share the user's number`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(ActionBarEvent.AddContact)
            runCurrent()

            assertEquals(listOf(Saved(USER, "Ada", "Lovelace", sharePhoneNumber = false)), contacts.saved)
            assertEquals(listOf<ActionBarEffect>(ActionBarEffect.ContactAdded), effects)
        }

    @Test
    fun `sharing the phone number goes to the other person`() =
        runTest(dispatcher) {
            bar(ChatActionBar.SharePhoneNumber)
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(ActionBarEvent.SharePhoneNumber)
            runCurrent()

            assertEquals(listOf(USER), actionBars.shared)
            assertEquals(listOf<ActionBarEffect>(ActionBarEffect.PhoneNumberShared), effects)
        }

    @Test
    fun `closing dismisses the bar quietly`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(ActionBarEvent.Dismiss)
            runCurrent()

            assertEquals(listOf(CHAT), actionBars.dismissed)
            assertTrue(effects.isEmpty())
        }

    @Test
    fun `nothing is sent before a tap`() =
        runTest(dispatcher) {
            viewModel()

            assertTrue(blockList.blocked.isEmpty())
            assertTrue(contacts.saved.isEmpty())
            assertTrue(actionBars.dismissed.isEmpty() && actionBars.shared.isEmpty())
        }

    private data class Saved(
        val userId: Long,
        val firstName: String,
        val lastName: String,
        val sharePhoneNumber: Boolean,
    )

    private class FakeActionBars : ChatActionBarRepository {
        val dismissed = mutableListOf<Long>()
        val shared = mutableListOf<Long>()

        override suspend fun dismiss(chatId: Long): Outcome<Unit> {
            dismissed += chatId
            return Outcome.Success(Unit)
        }

        override suspend fun sharePhoneNumber(userId: Long): Outcome<Unit> {
            shared += userId
            return Outcome.Success(Unit)
        }
    }

    private class FakeBlockList : BlockListRepository {
        val blocked = mutableListOf<MessageSender>()
        var result: Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun blockedSenders(
            offset: Int,
            limit: Int,
        ): Outcome<BlockedSendersPage> = Outcome.Failure(DomainError.NotFound)

        override suspend fun unblock(sender: MessageSender): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun block(sender: MessageSender): Outcome<Unit> =
            result.also { if (it is Outcome.Success) blocked += sender }
    }

    private class FakeContacts : ContactRepository {
        val saved = mutableListOf<Saved>()

        override fun contacts(): Flow<Outcome<ImmutableList<User>>> = flowOf(Outcome.Success(persistentListOf()))

        override suspend fun search(query: String): Outcome<ImmutableList<Long>> = Outcome.Success(persistentListOf())

        override suspend fun addByPhoneNumber(
            phoneNumber: String,
            firstName: String,
            lastName: String,
        ): Outcome<AddContactResult> = Outcome.Failure(DomainError.NotFound)

        override suspend fun addByUsername(
            username: String,
            firstName: String,
            lastName: String,
        ): Outcome<Long> = Outcome.Failure(DomainError.NotFound)

        override suspend fun editableContact(userId: Long): Outcome<EditableContact> =
            Outcome.Failure(DomainError.NotFound)

        override suspend fun save(
            userId: Long,
            firstName: String,
            lastName: String,
            sharePhoneNumber: Boolean,
            note: String?,
        ): Outcome<Unit> {
            saved += Saved(userId, firstName, lastName, sharePhoneNumber)
            return Outcome.Success(Unit)
        }

        override suspend fun delete(userId: Long): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun privateChatId(userId: Long): Outcome<Long> = Outcome.Success(userId)

        override suspend fun inviteLink(): String = ""
    }

    private companion object {
        const val CHAT = 42L
        const val USER = 42L
    }
}
