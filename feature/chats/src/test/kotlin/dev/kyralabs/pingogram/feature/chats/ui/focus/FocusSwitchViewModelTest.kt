package dev.kyralabs.pingogram.feature.chats.ui.focus

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.domain.model.FocusProfile
import dev.kyralabs.pingogram.core.domain.model.FocusSchedule
import dev.kyralabs.pingogram.core.domain.model.FocusUntil
import dev.kyralabs.pingogram.core.domain.model.ManualFocus
import dev.kyralabs.pingogram.core.testing.FakeFocusRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeWallClock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The chat list's Focus pill and switcher (board 31), on the clock's minute in UTC. */
@OptIn(ExperimentalCoroutinesApi::class)
class FocusSwitchViewModelTest {
    /** Monday 2026-11-16, 10:00 UTC. */
    private val now = at(16, 10)
    private val work =
        FocusProfile(
            id = 1,
            name = "Work hours",
            icon = FocusIcon.Work,
            allowedFolderIds = persistentListOf(3),
            allowedChatIds = persistentListOf(),
            schedule = FocusSchedule(DayOfWeek.entries.take(5).toImmutableSet(), 9 * 60, 18 * 60),
            allowsCallsFromContacts = true,
        )
    private val sleep =
        work.copy(
            id = 2,
            name = "Sleep",
            icon = FocusIcon.Sleep,
            schedule = FocusSchedule(DayOfWeek.entries.toImmutableSet(), 22 * 60 + 30, 7 * 60),
        )
    private val weekend = work.copy(id = 3, name = "Weekend", icon = FocusIcon.Weekend, schedule = null)
    private val repository = FakeFocusRepository(listOf(work, sleep, weekend))
    private val clock = FakeWallClock(now)
    private val savedStateHandle = SavedStateHandle()

    private fun at(
        day: Int,
        hour: Int,
        minute: Int = 0,
    ): Instant = LocalDateTime.of(2026, 11, day, hour, minute).toInstant(ZoneOffset.UTC)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): FocusSwitchViewModel {
        val viewModel = FocusSwitchViewModel(repository, clock, savedStateHandle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `the pill shows the profile on now and when it ends`() =
        runTest {
            val state = viewModel().uiState.value

            assertEquals(FocusPill("Work hours", FocusIcon.Work, at(16, 18).epochSecond), state.pill)
            assertEquals(
                listOf(
                    FocusProfileStatus.UntilScheduleEnds(at(16, 18).epochSecond),
                    FocusProfileStatus.StartsAt(at(16, 22, 30).epochSecond),
                    FocusProfileStatus.ByHand,
                ),
                state.profiles.map { it.status },
            )
            assertEquals(listOf(true, false, false), state.profiles.map { it.isOn })
            assertEquals(FocusKeep.ScheduleChange, state.keep)
            assertEquals(at(16, 18).epochSecond, state.scheduleChangeAt)
        }

    @Test
    fun `no pill while Focus is off or the header option is off`() =
        runTest {
            clock.setTime(at(16, 20))
            val viewModel = viewModel()
            assertNull(viewModel.uiState.value.pill)

            clock.setTime(now)
            runCurrent()
            repository.showsInChatList.value = false
            runCurrent()

            assertNull(viewModel.uiState.value.pill)
            assertTrue(
                viewModel.uiState.value.profiles
                    .first()
                    .isOn,
            )
        }

    @Test
    fun `the switcher opens and closes, and stays open across process death`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(FocusSwitchEvent.Open)
            runCurrent()
            assertTrue(viewModel.uiState.value.switcherOpen)
            assertEquals(true, savedStateHandle.get<Boolean>("focus.switcher_open"))

            viewModel.onEvent(FocusSwitchEvent.Close)
            runCurrent()
            assertFalse(viewModel.uiState.value.switcherOpen)
        }

    @Test
    fun `choosing a profile turns it on by hand until the schedule changes`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(FocusSwitchEvent.Choose(3))
            runCurrent()

            assertEquals(ManualFocus(3, now.epochSecond, FocusUntil.ScheduleChange), repository.manual.value)
            val state = viewModel.uiState.value
            assertEquals("Weekend", state.pill?.name)
            assertEquals(FocusProfileStatus.Until(at(16, 18).epochSecond), state.profiles[2].status)
        }

    @Test
    fun `Off turns Focus off, and an hour keeps the current choice for an hour`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(FocusSwitchEvent.Choose(null))
            runCurrent()
            assertNull(viewModel.uiState.value.pill)
            assertEquals(FocusKeep.ScheduleChange, viewModel.uiState.value.keep)

            viewModel.onEvent(FocusSwitchEvent.Keep(FocusKeep.OneHour))
            runCurrent()

            assertEquals(
                ManualFocus(null, now.epochSecond, FocusUntil.Time(now.epochSecond + 3_600)),
                repository.manual.value,
            )
            assertEquals(FocusKeep.OneHour, viewModel.uiState.value.keep)
        }

    @Test
    fun `until turned off keeps the profile on with no end`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(FocusSwitchEvent.Keep(FocusKeep.TurnedOff))
            runCurrent()

            assertEquals(ManualFocus(1, now.epochSecond, FocusUntil.TurnedOff), repository.manual.value)
            val state = viewModel.uiState.value
            assertNull(state.pill?.endsAt)
            assertEquals(FocusProfileStatus.UntilTurnedOff, state.profiles[0].status)
            assertEquals(FocusKeep.TurnedOff, state.keep)
        }

    @Test
    fun `without schedules a choice lasts until turned off`() =
        runTest {
            repository.profiles.value = persistentListOf(weekend)
            val viewModel = viewModel()
            assertNull(viewModel.uiState.value.scheduleChangeAt)

            viewModel.onEvent(FocusSwitchEvent.Choose(3))
            runCurrent()

            assertEquals(FocusUntil.TurnedOff, repository.manual.value?.until)
        }

    @Test
    fun `a change that fails says why and changes nothing`() =
        runTest {
            val viewModel = viewModel()
            val effects = mutableListOf<FocusSwitchEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
            repository.failure = DomainError.Network

            viewModel.onEvent(FocusSwitchEvent.Choose(2))
            runCurrent()

            assertEquals(listOf(FocusSwitchEffect.ShowError(DomainError.Network)), effects)
            assertNull(repository.manual.value)
            assertEquals(
                "Work hours",
                viewModel.uiState.value.pill
                    ?.name,
            )
        }

    @Test
    fun `a schedule with no days starts never, so it reads by hand`() =
        runTest {
            val never = FocusSchedule(persistentSetOf(), 0, 60)
            repository.profiles.value = persistentListOf(weekend.copy(schedule = never))

            assertEquals(
                FocusProfileStatus.ByHand,
                viewModel()
                    .uiState.value.profiles[0]
                    .status,
            )
        }
}
