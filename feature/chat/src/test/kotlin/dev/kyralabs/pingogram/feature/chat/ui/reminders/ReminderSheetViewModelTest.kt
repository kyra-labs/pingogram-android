package dev.kyralabs.pingogram.feature.chat.ui.reminders

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BookmarkPreview
import dev.kyralabs.pingogram.core.domain.model.Reminder
import dev.kyralabs.pingogram.core.testing.FakeReminderRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeMessageRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.chat.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeWallClock
import dev.kyralabs.pingogram.feature.chat.ui.ReminderEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
import java.time.Instant

/** Setting a reminder of a message (the owner's board 29b): the choices, setting, undoing, editing and removing. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSheetViewModelTest {
    private val reminders = FakeReminderRepository()
    private val messages = FakeMessageRepository()
    private val users = FakeUserRepository()
    private val clock = FakeWallClock(NOW)

    // NOW is Sunday, October 18, 2026, 9:41:30 UTC.
    private val inOneHour = Instant.parse("2026-10-18T10:41:00Z").epochSecond
    private val thisEvening = Instant.parse("2026-10-18T20:00:00Z").epochSecond
    private val tomorrowMorning = Instant.parse("2026-10-19T09:00:00Z").epochSecond

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        messages.onMessage = { _, id -> Outcome.Success(message(id)) }
        users.setUsers(user(ADA, firstName = "Ada", lastName = "Lovelace"))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(handle: SavedStateHandle = SavedStateHandle()): ReminderSheetViewModel {
        val viewModel =
            ReminderSheetViewModel(
                PRIVATE_CHAT,
                handle,
                reminders,
                messages,
                FakeChatRepository(),
                users,
                clock,
                FakeTimePatterns(),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sheet.collect {} }
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: ReminderSheetViewModel): List<ReminderEffect> {
        val effects = mutableListOf<ReminderEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun save(viewModel: ReminderSheetViewModel) =
        viewModel.onEvent(ReminderEvent.Save(note = " Book it ", sender = "Ada Lovelace", text = "Hello"))

    @Test
    fun `opening shows the message and the choices with their times, in an hour chosen`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onEvent(ReminderEvent.Open(42))
            runCurrent()

            val sheet = viewModel.sheet.value!!
            assertEquals(UiText.Plain("Ada Lovelace"), sheet.sender)
            assertEquals(ReminderOption.InOneHour, sheet.selected)
            assertEquals(
                listOf(inOneHour, thisEvening, tomorrowMorning, tomorrowMorning, null),
                sheet.choices.map { it.at },
            )
            assertEquals(false, sheet.hasReminder)
        }

    @Test
    fun `this evening is left out once 8 PM has passed`() =
        runTest {
            clock.setTime(Instant.parse("2026-10-18T20:30:00Z"))
            val viewModel = createViewModel()

            viewModel.onEvent(ReminderEvent.Open(42))

            assertTrue(
                viewModel.sheet.value!!
                    .choices
                    .none { it.option == ReminderOption.ThisEvening },
            )
        }

    @Test
    fun `setting keeps the choice's time, the note and what the message said, and offers undo`() =
        runTest {
            val viewModel = createViewModel()
            val effects = effectsOf(viewModel)
            viewModel.onEvent(ReminderEvent.Open(42))
            viewModel.onEvent(ReminderEvent.Choose(ReminderOption.TomorrowMorning))

            save(viewModel)
            runCurrent()

            assertEquals(
                listOf(
                    Reminder(PRIVATE_CHAT, 42, tomorrowMorning, "Book it", BookmarkPreview("Ada Lovelace", "Hello")),
                ),
                reminders.saved.value,
            )
            assertNull(viewModel.sheet.value)
            assertTrue(effects.single() is ReminderEffect.Set)

            viewModel.onEvent(ReminderEvent.Undo)
            runCurrent()
            assertTrue(reminders.saved.value.isEmpty())
        }

    @Test
    fun `a picked date and time is chosen, and survives process death`() =
        runTest {
            val handle = SavedStateHandle()
            val viewModel = createViewModel(handle)
            val picked = Instant.parse("2026-10-25T07:30:00Z").epochSecond
            viewModel.onEvent(ReminderEvent.Open(42))
            viewModel.onEvent(ReminderEvent.PickCustom(picked))

            val restored = createViewModel(handle)
            runCurrent()

            assertEquals(ReminderOption.Custom, restored.sheet.value!!.selected)
            assertEquals(picked, restored.sheet.value!!.at)
        }

    @Test
    fun `a time already past is not set`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onEvent(ReminderEvent.Open(42))
            viewModel.onEvent(ReminderEvent.PickCustom(NOW.epochSecond - 60))

            save(viewModel)

            assertTrue(reminders.saved.value.isEmpty())
        }

    @Test
    fun `a message with a reminder opens at its time, and undoing an edit puts it back`() =
        runTest {
            val existing = Reminder(PRIVATE_CHAT, 42, thisEvening, note = "Call back")
            reminders.saved.value = listOf(existing)
            val viewModel = createViewModel()

            viewModel.onEvent(ReminderEvent.Open(42))
            runCurrent()
            val sheet = viewModel.sheet.value!!
            assertEquals(ReminderOption.Custom, sheet.selected)
            assertEquals(thisEvening, sheet.at)
            assertEquals("Call back", sheet.note)
            assertTrue(sheet.hasReminder)

            viewModel.onEvent(ReminderEvent.Choose(ReminderOption.TomorrowMorning))
            save(viewModel)
            runCurrent()
            viewModel.onEvent(ReminderEvent.Undo)
            runCurrent()

            assertEquals(listOf(existing), reminders.saved.value)
        }

    @Test
    fun `removing deletes the reminder`() =
        runTest {
            reminders.saved.value = listOf(Reminder(PRIVATE_CHAT, 42, thisEvening))
            val viewModel = createViewModel()
            val effects = effectsOf(viewModel)
            viewModel.onEvent(ReminderEvent.Open(42))

            viewModel.onEvent(ReminderEvent.Remove)
            runCurrent()

            assertTrue(reminders.saved.value.isEmpty())
            assertEquals(listOf<ReminderEffect>(ReminderEffect.Removed), effects)
        }

    @Test
    fun `a reminder that cannot be saved says why and keeps the sheet`() =
        runTest {
            val viewModel = createViewModel()
            val effects = effectsOf(viewModel)
            reminders.failure = DomainError.Network
            viewModel.onEvent(ReminderEvent.Open(42))

            save(viewModel)
            runCurrent()

            assertEquals(listOf<ReminderEffect>(ReminderEffect.ShowError(DomainError.Network)), effects)
            assertEquals(42L, viewModel.sheet.value?.messageId)
        }

    @Test
    fun `dismissing closes the sheet`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onEvent(ReminderEvent.Open(42))

            viewModel.onEvent(ReminderEvent.Dismiss)

            assertNull(viewModel.sheet.value)
        }
}
