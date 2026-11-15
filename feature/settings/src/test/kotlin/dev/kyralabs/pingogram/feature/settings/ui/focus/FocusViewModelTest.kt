package dev.kyralabs.pingogram.feature.settings.ui.focus

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.domain.model.FocusProfile
import dev.kyralabs.pingogram.core.domain.model.FocusSchedule
import dev.kyralabs.pingogram.core.domain.model.FocusUntil
import dev.kyralabs.pingogram.core.testing.FakeFocusRepository
import dev.kyralabs.pingogram.feature.settings.ui.FakeWallClock
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModelTest {
    // Monday 2026-11-16, 10:00 UTC.
    private val now = Instant.parse("2026-11-16T10:00:00Z")
    private val work =
        FocusProfile(
            id = 1,
            name = "Work hours",
            icon = FocusIcon.Work,
            allowedFolderIds = persistentListOf(3, 4),
            allowedChatIds = persistentListOf(10L),
            schedule = FocusSchedule(persistentSetOf(DayOfWeek.MONDAY), 9 * 60, 18 * 60),
            allowsCallsFromContacts = true,
        )
    private val sleep = work.copy(id = 2, name = "Sleep", icon = FocusIcon.Sleep, schedule = null)
    private val repository = FakeFocusRepository(listOf(work, sleep))

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): FocusViewModel {
        val viewModel = FocusViewModel(repository, FakeWallClock(now))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `shows nothing before the profiles are read`() =
        runTest {
            assertNull(FocusViewModel(repository, FakeWallClock(now)).uiState.value.profiles)
        }

    @Test
    fun `lists the profiles, the one its schedule covers on`() =
        runTest {
            val profiles = viewModel().uiState.value.profiles!!

            assertEquals(listOf(1L, 2L), profiles.map { it.id })
            assertEquals(listOf(true, false), profiles.map { it.isOn })
            assertEquals(2, profiles[0].folderCount)
            assertEquals(1, profiles[0].chatCount)
        }

    @Test
    fun `turning a profile on sets it by hand until the schedule changes`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(FocusEvent.Toggle(2, on = true))
            runCurrent()

            val manual = repository.manual.value!!
            assertEquals(2L, manual.profileId)
            assertEquals(FocusUntil.ScheduleChange, manual.until)
            assertEquals(now.epochSecond, manual.setAt)
            assertEquals(listOf(false, true), viewModel.uiState.value.profiles!!.map { it.isOn })
        }

    @Test
    fun `turning the profile on now off turns Focus off until the schedule changes`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(FocusEvent.Toggle(1, on = false))
            runCurrent()

            assertNull(repository.manual.value!!.profileId)
            assertEquals(listOf(false, false), viewModel.uiState.value.profiles!!.map { it.isOn })
        }

    @Test
    fun `a change that fails says why`() =
        runTest {
            val viewModel = viewModel()
            val effects = mutableListOf<FocusEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
            repository.failure = DomainError.Network

            viewModel.onEvent(FocusEvent.Toggle(2, on = true))
            runCurrent()

            assertEquals(listOf(FocusEffect.ShowError(DomainError.Network)), effects)
            assertNull(repository.manual.value)
        }
}
