package dev.kyralabs.pingogram.feature.settings.ui.focus

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.domain.model.FocusProfile
import dev.kyralabs.pingogram.core.domain.model.FocusSchedule
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.testing.FakeFocusRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val folders =
        MutableStateFlow<ImmutableList<ChatFolder>>(
            persistentListOf(ChatFolder(3, FormattedText("Family", persistentListOf()), "Custom", null)),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(
        id: Long,
        template: FocusTemplate? = null,
        handle: SavedStateHandle = SavedStateHandle(),
    ): FocusEditViewModel {
        val viewModel = FocusEditViewModel(id, template, repository, folders, handle)
        runCurrent()
        return viewModel
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
}
