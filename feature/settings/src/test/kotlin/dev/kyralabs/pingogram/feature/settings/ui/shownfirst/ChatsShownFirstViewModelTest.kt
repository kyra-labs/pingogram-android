package dev.kyralabs.pingogram.feature.settings.ui.shownfirst

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeChatsShownFirstRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Chats shown first: the chats in their order, adding through the picker up to the most, removing, moving, errors. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatsShownFirstViewModelTest {
    private val client = FakeTdlibClient()
    private val savedState = SavedStateHandle()
    private val shownFirst = FakeChatsShownFirstRepository(listOf(TEAM, ADA))

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
    private suspend fun TestScope.viewModel(handle: SavedStateHandle = savedState): ChatsShownFirstViewModel {
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        runCurrent()
        CHATS.forEachIndexed { index, (id, title) ->
            val chat = TdApiFixtures.chat(id = id, type = TdApi.ChatTypePrivate(id), title = title)
            chat.positions = arrayOf(TdApiFixtures.chatPosition(order = (CHATS.size - index).toLong()))
            client.emit(TdApiFixtures.updateNewChat(chat))
        }
        runCurrent()
        return ChatsShownFirstViewModel(handle, shownFirst, chats).also {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.pickerState.collect {} }
            runCurrent()
        }
    }

    private fun TestScope.send(
        viewModel: ChatsShownFirstViewModel,
        vararg events: ChatsShownFirstEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    private val ChatsShownFirstViewModel.content get() = uiState.value as ChatsShownFirstUiState.Content

    @Test
    fun `shows the chats in their order with their names`() =
        runTest {
            val content = viewModel().content

            assertEquals(listOf("Team", "Ada"), content.chats.map { it.title })
            assertEquals(true, content.canAdd)
        }

    @Test
    fun `the picker offers the other chats, takes as many as fit, and adds them at the end`() =
        runTest {
            shownFirst.ids.value = persistentListOf(TEAM, ADA, GRACE)
            val viewModel = viewModel()

            send(viewModel, ChatsShownFirstEvent.OpenPicker)
            val picker = viewModel.pickerState.value!!
            assertEquals(listOf(ALAN, CHARLES, KATHERINE), picker.candidates.map { it.id })
            assertEquals(2, picker.slots)

            send(
                viewModel,
                ChatsShownFirstEvent.TogglePicked(KATHERINE),
                ChatsShownFirstEvent.TogglePicked(ALAN),
                ChatsShownFirstEvent.TogglePicked(CHARLES),
                ChatsShownFirstEvent.ClosePicker,
            )

            assertNull(viewModel.pickerState.value)
            assertEquals(listOf(TEAM, ADA, GRACE, KATHERINE, ALAN), shownFirst.ids.value)
            assertFalse(viewModel.content.canAdd)
        }

    @Test
    fun `the picker filters by name and survives process death`() =
        runTest {
            val viewModel = viewModel()
            send(
                viewModel,
                ChatsShownFirstEvent.OpenPicker,
                ChatsShownFirstEvent.PickerQueryChanged("al"),
                ChatsShownFirstEvent.TogglePicked(ALAN),
            )
            assertEquals(
                listOf(ALAN),
                viewModel.pickerState.value!!
                    .candidates
                    .map { it.id },
            )

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))
            val picker = restored.pickerState.value!!
            assertEquals("al", picker.query)
            assertEquals(listOf(true), picker.candidates.map { it.selected })
        }

    @Test
    fun `removes and moves a chat`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, ChatsShownFirstEvent.Move(ADA, up = true))
            assertEquals(listOf(ADA, TEAM), shownFirst.ids.value)

            send(viewModel, ChatsShownFirstEvent.Move(ADA, up = true))
            assertEquals(listOf(ADA, TEAM), shownFirst.ids.value)

            send(viewModel, ChatsShownFirstEvent.Remove(ADA))
            assertEquals(listOf(TEAM), shownFirst.ids.value)
        }

    @Test
    fun `a change that cannot be saved says why`() =
        runTest {
            val viewModel = viewModel()
            val effects = mutableListOf<ChatsShownFirstEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
            shownFirst.failure = DomainError.Network

            send(viewModel, ChatsShownFirstEvent.Remove(TEAM))

            assertEquals(listOf(ChatsShownFirstEffect.ShowError(DomainError.Network)), effects)
            assertEquals(listOf(TEAM, ADA), shownFirst.ids.value)
        }

    private companion object {
        const val TEAM = 2L
        const val ADA = 5L
        const val GRACE = 6L
        const val ALAN = 7L
        const val CHARLES = 8L
        const val KATHERINE = 9L
        val CHATS =
            listOf(
                TEAM to "Team",
                ADA to "Ada",
                GRACE to "Grace",
                ALAN to "Alan",
                CHARLES to "Charles",
                KATHERINE to "Katherine",
            )
    }
}
