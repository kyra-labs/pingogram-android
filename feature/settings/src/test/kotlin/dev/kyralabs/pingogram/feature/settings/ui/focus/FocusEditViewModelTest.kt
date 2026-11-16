package dev.kyralabs.pingogram.feature.settings.ui.focus

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.domain.model.FocusProfile
import dev.kyralabs.pingogram.core.domain.model.FocusSchedule
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeFocusRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek

@OptIn(ExperimentalCoroutinesApi::class)
class FocusEditViewModelTest {
    private val sleep =
        FocusProfile(
            id = 5,
            name = "Sleep",
            icon = FocusIcon.Sleep,
            allowedFolderIds = persistentListOf(3),
            allowedChatIds = persistentListOf(10L),
            schedule = FocusSchedule(persistentSetOf(DayOfWeek.MONDAY), 22 * 60 + 30, 7 * 60),
            allowsCallsFromContacts = true,
        )
    private val repository = FakeFocusRepository(listOf(sleep))
    private val client = FakeTdlibClient()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        client.on(TdApi.LoadChats::class, TdResult.Failure(TdError(404, "Not Found")))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.viewModel(
        id: Long,
        template: FocusTemplate? = null,
        handle: SavedStateHandle = SavedStateHandle(),
    ): FocusEditViewModel {
        // Through the real chat repository on FakeTdlibClient, TDLib having announced the folders and the chats.
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        runCurrent()
        client.emit(TdApiFixtures.updateChatFolders(TdApiFixtures.chatFolderInfo(id = 3, title = "Family")))
        CHATS.forEachIndexed { index, (chatId, title) ->
            val chat = TdApiFixtures.chat(id = chatId, type = TdApi.ChatTypePrivate(chatId), title = title)
            chat.positions = arrayOf(TdApiFixtures.chatPosition(order = (CHATS.size - index).toLong()))
            client.emit(TdApiFixtures.updateNewChat(chat))
        }
        runCurrent()
        val viewModel = FocusEditViewModel(id, template, repository, chats, handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.chatPicker.collect {} }
        runCurrent()
        return viewModel
    }

    private companion object {
        val CHATS = listOf(10L to "Ada", 11L to "Alan", 12L to "Grace")
    }

    @Test
    fun `edits a saved profile`() =
        runTest {
            val state = viewModel(5).uiState.value

            assertFalse(state.isNew)
            val draft = state.draft!!
            assertEquals("Sleep", draft.name)
            assertTrue(draft.scheduled)
            assertEquals(listOf(FocusFolderChoice(3, "Family")), state.folders)
        }

    @Test
    fun `a new profile from a template takes the template's name once`() =
        runTest {
            val viewModel = viewModel(0, FocusTemplate.WorkHours)

            viewModel.onEvent(FocusEditEvent.Prefill("Work hours"))
            viewModel.onEvent(FocusEditEvent.Name("Deep work"))
            viewModel.onEvent(FocusEditEvent.Prefill("Work hours"))

            val draft = viewModel.uiState.value.draft!!
            assertEquals("Deep work", draft.name)
            assertEquals(FocusIcon.Work, draft.icon)
            assertEquals(9 * 60, draft.startMinute)
        }

    @Test
    fun `saves the draft and closes`() =
        runTest {
            val viewModel = viewModel(5)
            val effects = mutableListOf<FocusEditEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            viewModel.onEvent(FocusEditEvent.Folder(3, allowed = false))
            viewModel.onEvent(FocusEditEvent.Day(DayOfWeek.TUESDAY, on = true))
            viewModel.onEvent(FocusEditEvent.Time(FocusTimeEnd.End, 6 * 60))
            viewModel.onEvent(FocusEditEvent.Calls(false))
            viewModel.onEvent(FocusEditEvent.Save)
            runCurrent()

            val saved = repository.profiles.value.single()
            assertEquals(emptyList<Int>(), saved.allowedFolderIds)
            assertEquals(listOf(10L), saved.allowedChatIds)
            assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), saved.schedule!!.days)
            assertEquals(6 * 60, saved.schedule!!.endMinute)
            assertFalse(saved.allowsCallsFromContacts)
            assertEquals(listOf(FocusEditEffect.Done), effects)
        }

    @Test
    fun `by hand saves no schedule`() =
        runTest {
            val viewModel = viewModel(5)

            viewModel.onEvent(FocusEditEvent.Scheduled(false))
            viewModel.onEvent(FocusEditEvent.Save)
            runCurrent()

            assertNull(
                repository.profiles.value
                    .single()
                    .schedule,
            )
        }

    @Test
    fun `refuses a profile without a name`() =
        runTest {
            val viewModel = viewModel(0, FocusTemplate.Blank)
            val effects = mutableListOf<FocusEditEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            viewModel.onEvent(FocusEditEvent.Name("  "))
            viewModel.onEvent(FocusEditEvent.Save)
            runCurrent()

            assertTrue(viewModel.uiState.value.nameMissing)
            assertEquals(listOf(FocusEditEffect.Rejected), effects)
            assertEquals(1, repository.profiles.value.size)
        }

    @Test
    fun `deletes after asking`() =
        runTest {
            val viewModel = viewModel(5)

            viewModel.onEvent(FocusEditEvent.Delete)
            assertTrue(viewModel.uiState.value.askDelete)
            viewModel.onEvent(FocusEditEvent.DeleteConfirmed)
            runCurrent()

            assertTrue(repository.profiles.value.isEmpty())
        }

    @Test
    fun `a failed save says why and keeps the draft`() =
        runTest {
            val viewModel = viewModel(5)
            val effects = mutableListOf<FocusEditEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
            repository.failure = DomainError.Network

            viewModel.onEvent(FocusEditEvent.Save)
            runCurrent()

            assertEquals(listOf(FocusEditEffect.ShowError(DomainError.Network)), effects)
            assertFalse(viewModel.uiState.value.isBusy)
        }

    @Test
    fun `the draft and the question survive process death`() =
        runTest {
            val handle = SavedStateHandle()
            val first = viewModel(5, handle = handle)
            first.onEvent(FocusEditEvent.Name("Night"))
            first.onEvent(FocusEditEvent.Icon(FocusIcon.Reading))
            first.onEvent(FocusEditEvent.Delete)

            val restored = viewModel(5, handle = handle).uiState.value

            val draft = restored.draft!!
            assertEquals("Night", draft.name)
            assertEquals(FocusIcon.Reading, draft.icon)
            assertEquals(persistentSetOf(3), draft.folderIds)
            assertTrue(restored.askDelete)
        }

    @Test
    fun `names the chats it also allows`() =
        runTest {
            val state = viewModel(5).uiState.value

            assertEquals(listOf(FocusChatChoice(10, "Ada", null)), state.allowedChats)
        }

    @Test
    fun `the picker offers the other chats and adds the picks as it closes`() =
        runTest {
            val viewModel = viewModel(5)

            viewModel.onEvent(FocusChatEvent.Add)
            runCurrent()
            val picker = viewModel.chatPicker.value!!
            assertEquals(listOf(11L, 12L), picker.candidates.map { it.chat.id })

            viewModel.onEvent(FocusChatEvent.Query("gra"))
            runCurrent()
            assertEquals(
                listOf("Grace"),
                viewModel.chatPicker.value!!
                    .candidates
                    .map { it.chat.title },
            )

            viewModel.onEvent(FocusChatEvent.Toggle(12))
            runCurrent()
            assertTrue(
                viewModel.chatPicker.value!!
                    .candidates
                    .single()
                    .selected,
            )
            viewModel.onEvent(FocusChatEvent.Close)
            runCurrent()

            assertNull(viewModel.chatPicker.value)
            assertEquals(
                listOf(10L, 12L),
                viewModel.uiState.value.draft!!
                    .chatIds,
            )
            assertEquals(
                listOf("Ada", "Grace"),
                viewModel.uiState.value.allowedChats
                    .map { it.title },
            )
        }

    @Test
    fun `removes a chat and saves the rest`() =
        runTest {
            val viewModel = viewModel(5)

            viewModel.onEvent(FocusChatEvent.Remove(10))
            viewModel.onEvent(FocusEditEvent.Save)
            runCurrent()

            assertTrue(
                viewModel.uiState.value.allowedChats
                    .isEmpty(),
            )
            assertEquals(
                emptyList<Long>(),
                repository.profiles.value
                    .single()
                    .allowedChatIds,
            )
        }

    @Test
    fun `the open picker and its picks survive process death`() =
        runTest {
            val handle = SavedStateHandle()
            val first = viewModel(5, handle = handle)
            first.onEvent(FocusChatEvent.Add)
            first.onEvent(FocusChatEvent.Query("Al"))
            first.onEvent(FocusChatEvent.Toggle(11))
            runCurrent()

            val restored = viewModel(5, handle = handle).chatPicker.value!!

            assertEquals("Al", restored.query)
            assertTrue(restored.candidates.single { it.chat.id == 11L }.selected)
        }
}
