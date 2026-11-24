package dev.kyralabs.pingogram.feature.contacts.ui.birthdays

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Birthdate
import dev.kyralabs.pingogram.core.domain.model.BirthdayReminder
import dev.kyralabs.pingogram.core.domain.model.CloseBirthday
import dev.kyralabs.pingogram.core.testing.FakeBirthdayReminderRepository
import dev.kyralabs.pingogram.core.testing.FakeBirthdayRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.contacts.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.contacts.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.contacts.ui.FakeWallClock
import dev.kyralabs.pingogram.feature.contacts.ui.contact
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
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
import java.time.Instant
import java.util.Locale

/**
 * [BirthdaysViewModel] (`REQUIREMENTS.md` FR-X18): the close birthdays grouped around today with their dates and ages,
 * nothing until names are known, the reminders set and cleared (and one that fails), adding to the calendar, and a tap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BirthdaysViewModelTest {
    private val birthdays = FakeBirthdayRepository()
    private val reminders = FakeBirthdayReminderRepository()
    private val users =
        FakeUserRepository().apply {
            userMap.value =
                persistentMapOf(1L to contact(1, "Ada"), 2L to contact(2, "Grace"), 3L to contact(3, "Alan"))
        }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): BirthdaysViewModel {
        val viewModel =
            BirthdaysViewModel(
                birthdayRepository = birthdays,
                reminderRepository = reminders,
                userRepository = users,
                clock = FakeWallClock(NOW),
                timePatterns = FakeTimePatterns(),
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: BirthdaysViewModel): List<BirthdaysEffect> {
        val effects = mutableListOf<BirthdaysEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun announce(vararg close: CloseBirthday) {
        birthdays.announced.value = close.toList().toPersistentList()
    }

    private val BirthdaysViewModel.content get() = uiState.value as BirthdaysUiState.Content

    @Test
    fun `birthdays are grouped as today, upcoming and just past, with the date and the age they turn`() =
        runTest {
            announce(
                CloseBirthday(2, Birthdate(25, 11, null)),
                CloseBirthday(1, Birthdate(24, 11, 1990)),
                CloseBirthday(3, Birthdate(23, 11, 1912)),
            )

            val content = viewModel().content

            assertEquals(listOf(1L), content.today.map { it.userId })
            assertEquals("November 24", content.today.single().date)
            assertEquals(36, content.today.single().turns)
            assertEquals(listOf(2L), content.upcoming.map { it.userId })
            assertEquals(null, content.upcoming.single().turns)
            assertEquals(listOf(3L), content.past.map { it.userId })
        }

    @Test
    fun `a birthday across the new year is upcoming, not eleven months away`() =
        runTest {
            announce(CloseBirthday(1, Birthdate(1, 1, 2000)))
            val content = viewModel().content

            assertEquals(listOf(1L), content.upcoming.map { it.userId })
            assertEquals("January 1", content.upcoming.single().date)
            assertEquals(27, content.upcoming.single().turns)
            assertEquals(0, content.today.size + content.past.size)
        }

    @Test
    fun `none, or only contacts not named yet, is the empty state`() =
        runTest {
            announce(CloseBirthday(9, Birthdate(24, 11, null)))

            assertEquals(BirthdaysUiState.Empty, viewModel().uiState.value)
        }

    @Test
    fun `remind me keeps a reminder for the day and month, and turning it off deletes it`() =
        runTest {
            announce(CloseBirthday(1, Birthdate(24, 11, 1990)))
            val viewModel = viewModel()

            viewModel.onEvent(BirthdaysEvent.SetReminder(1, on = true))
            runCurrent()
            assertEquals(listOf(BirthdayReminder(1, 24, 11)), reminders.all.value)
            assertEquals(
                true,
                viewModel.content.today
                    .single()
                    .remind,
            )

            viewModel.onEvent(BirthdaysEvent.SetReminder(1, on = false))
            runCurrent()
            assertEquals(emptyList<BirthdayReminder>(), reminders.all.value)
            assertEquals(
                false,
                viewModel.content.today
                    .single()
                    .remind,
            )
        }

    @Test
    fun `a reminder that cannot be saved says so`() =
        runTest {
            announce(CloseBirthday(1, Birthdate(24, 11, 1990)))
            reminders.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(BirthdaysEvent.SetReminder(1, on = true))
            runCurrent()

            assertEquals(
                listOf<BirthdaysEffect>(BirthdaysEffect.ReminderFailed(DomainError.Unknown(0, "Disk full"))),
                effects,
            )
        }

    @Test
    fun `add to calendar starts from the next date, and a tap opens the chat`() =
        runTest {
            announce(CloseBirthday(1, Birthdate(24, 11, 1990)), CloseBirthday(3, Birthdate(23, 11, null)))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(BirthdaysEvent.AddToCalendar(1))
            viewModel.onEvent(BirthdaysEvent.AddToCalendar(3))
            viewModel.onEvent(BirthdaysEvent.Open(1))
            runCurrent()

            assertEquals(
                listOf(
                    BirthdaysEffect.AddToCalendar(UiText.Plain("Ada"), 2026, 11, 24),
                    BirthdaysEffect.AddToCalendar(UiText.Plain("Alan"), 2027, 11, 23),
                    BirthdaysEffect.OpenChat(1),
                ),
                effects,
            )
        }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-24T12:00:00Z")
    }
}
