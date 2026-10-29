package dev.kyralabs.pingogram.feature.settings.ui.lockedchats

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeChatLockRepository
import dev.kyralabs.pingogram.core.testing.FakePasscodeRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
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
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LockedChatsViewModelTest {
    private val client = FakeTdlibClient()
    private val savedState = SavedStateHandle()
    private val passcodes = FakePasscodeRepository(passcode = "1234")
    private val chatLock = FakeChatLockRepository(locked = setOf(5L), unlocked = true)

    private val adaChat =
        TdApiFixtures.chat(id = 5, type = TdApi.ChatTypePrivate(5), title = "Ada").apply { unreadCount = 2 }
    private val team = TdApiFixtures.chat(id = -100, type = TdApi.ChatTypeBasicGroup(1), title = "Team")
    private val news = TdApiFixtures.chat(id = -200, type = TdApi.ChatTypeSupergroup(2, true), title = "News")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        client.on(TdApi.LoadChats::class, TdResult.Failure(TdError(404, "Not Found")))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Through the real chat repository on FakeTdlibClient, TDLib having announced the chats.
    private suspend fun TestScope.viewModel(handle: SavedStateHandle = savedState): LockedChatsViewModel {
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        runCurrent()
        listOf(adaChat, team, news).forEach {
            client.emit(TdApiFixtures.updateNewChat(it.apply { positions = arrayOf(TdApiFixtures.chatPosition()) }))
        }
        runCurrent()
        return LockedChatsViewModel(handle, chatLock, passcodes, chats).also {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.pickerState.collect {} }
            runCurrent()
        }
    }

    private fun TestScope.send(
        viewModel: LockedChatsViewModel,
        vararg events: LockedChatsEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    private val LockedChatsViewModel.content get() = uiState.value as LockedChatsUiState.Content

    @Test
    fun `shows the locked chats with their names and unread messages once opened`() =
        runTest {
            val rows = viewModel().content.chats

            assertEquals(listOf(LockedChatRow(5, "Ada", null, 2)), rows)
        }

    @Test
    fun `asks for the passcode while the chats are closed, and needs the passcode lock on`() =
        runTest {
            chatLock.setUnlocked(false)
            val viewModel = viewModel()
            assertEquals(LockedChatsUiState.Locked, viewModel.uiState.value)

            passcodes.removePasscode()
            runCurrent()
            assertEquals(LockedChatsUiState.NoPasscode, viewModel.uiState.value)
        }

    @Test
    fun `the picker offers the chats not locked yet, filtered by the query, and locks those picked`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, LockedChatsEvent.OpenPicker)
            assertEquals(
                listOf(-100L, -200L),
                viewModel.pickerState.value!!
                    .candidates
                    .map { it.id },
            )

            send(viewModel, LockedChatsEvent.PickerQueryChanged("te"), LockedChatsEvent.TogglePicked(-100))
            val picker = viewModel.pickerState.value!!
            assertEquals(listOf(-100L), picker.candidates.map { it.id })
            assertEquals(true, picker.candidates.single().selected)

            send(viewModel, LockedChatsEvent.ClosePicker)
            assertNull(viewModel.pickerState.value)
            assertEquals(listOf(5L, -100L), viewModel.content.chats.map { it.id })
        }

    @Test
    fun `unlocking a chat takes it out of the locked chats`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, LockedChatsEvent.Unlock(5))

            assertEquals(emptyList<LockedChatRow>(), viewModel.content.chats)
        }

    @Test
    fun `a change that cannot be saved is shown`() =
        runTest {
            chatLock.failure = DomainError.Network
            val viewModel = viewModel()
            val effects = mutableListOf<LockedChatsEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            send(viewModel, LockedChatsEvent.Unlock(5))

            assertEquals(listOf(LockedChatsEffect.ShowError(DomainError.Network)), effects)
            assertEquals(listOf(5L), viewModel.content.chats.map { it.id })
        }

    @Test
    fun `the picker survives process death`() =
        runTest {
            send(viewModel(), LockedChatsEvent.OpenPicker, LockedChatsEvent.TogglePicked(-200))

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertArrayEquals(longArrayOf(-200), savedState.get<LongArray>("lockedChats.chats"))
            assertEquals(
                listOf(-200L),
                restored.pickerState.value!!
                    .candidates
                    .filter { it.selected }
                    .map { it.id },
            )
        }
}
