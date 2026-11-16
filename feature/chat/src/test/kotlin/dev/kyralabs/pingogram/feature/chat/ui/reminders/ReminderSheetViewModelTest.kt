package dev.kyralabs.pingogram.feature.chat.ui.reminders

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import dev.kyralabs.pingogram.core.testing.FakeReminderRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
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
import java.util.Locale
import dev.kyralabs.pingogram.core.ui.R as UiR

/** Setting a reminder on a message (the owner's board 29b): the choices, setting, undoing, deleting. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSheetViewModelTest {
    private val reminders = FakeReminderRepository()
    private val messages = FakeMessageRepository()
    private val users = FakeUserRepository()
    private val chats = FakeChatRepository()

    // Sunday, October 18, 9:41:30 AM UTC.
    private val clock = FakeWallClock(NOW)

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

    private fun TestScope.createViewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        locale: Locale = Locale.US,
        chatId: Long = PRIVATE_CHAT,
    ): ReminderSheetViewModel {
        val viewModel =
            ReminderSheetViewModel(
                chatId = chatId,
                savedStateHandle = handle,
                reminders = reminders,
                messages = messages,
                chats = chats,
                users = users,
                clock = clock,
                timePatterns = FakeTimePatterns(),
                locale = { locale },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sheet.collect {} }
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: ReminderSheetViewModel): List<ReminderEffect> {
        val effects = mutableListOf<ReminderEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun day(
        weekday: String,
        time: String,
    ) = UiText.resource(UiR.string.reminder_label_day_time, weekday, time)

    @Test
    fun `in a group the message follows its sender's name`() =
        runTest {
            chats.chats.value = mapOf(GROUP_CHAT to ChatFixtures.groupChat)
            val viewModel = createViewModel(chatId = GROUP_CHAT)

            viewModel.onEvent(ReminderEvent.Open(42))
            runCurrent()

            assertEquals(UiText.Plain("Ada Lovelace"), viewModel.sheet.value!!.sender)
        }

    @Test
    fun `opening shows the message and the choices with their times, tomorrow morning chosen`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onEvent(ReminderEvent.Open(42))
            runCurrent()

            val sheet = viewModel.sheet.value!!
            // A private chat's name already says who wrote: the sender shows in groups only (board 29b).
            assertNull(sheet.sender)
            assertEquals(UiText.Plain("Message 42"), sheet.text)
            assertEquals(
                listOf(
                    ReminderOption(ReminderChoice.InOneHour, UiText.Plain("10:41 AM")),
                    ReminderOption(ReminderChoice.ThisEvening, UiText.Plain("8:00 PM")),
                    ReminderOption(ReminderChoice.TomorrowMorning, day("Mon", "9:00 AM")),
                    // Owner decision 2026-11-16: next week starts on the language's first day, Sunday in US English.
                    ReminderOption(ReminderChoice.NextWeek, day("Sun", "9:00 AM")),
                    ReminderOption(ReminderChoice.Custom, null),
                ),
                sheet.options,
            )
            assertEquals(ReminderChoice.TomorrowMorning, sheet.chosen)
            assertEquals(false, sheet.isExisting)
        }

    @Test
    fun `next week starts on the language's first day, worded by its weekday`() =
        runTest {
            // US English with weeks that start on Monday (a Unicode extension), which is tomorrow here.
            val viewModel = createViewModel(locale = Locale.forLanguageTag("en-US-u-fw-mon"))

            viewModel.onEvent(ReminderEvent.Open(42))
            runCurrent()

            val nextWeek =
                viewModel.sheet.value!!
                    .options
                    .single { it.choice == ReminderChoice.NextWeek }
            assertEquals(day("Mon", "9:00 AM"), nextWeek.time)
        }

    @Test
    fun `after 20 00, this evening is not offered`() =
        runTest {
            clock.setTime(Instant.parse("2026-10-18T21:00:00Z"))
            val viewModel = createViewModel()

            viewModel.onEvent(ReminderEvent.Open(42))
            runCurrent()

            assertTrue(
                viewModel.sheet.value!!
                    .options
                    .none { it.choice == ReminderChoice.ThisEvening },
            )
        }

    @Test
    fun `setting keeps the reminder with its note, says when it rings, and undo takes it back`() =
        runTest {
            val viewModel = createViewModel()
            val effects = effectsOf(viewModel)
            viewModel.onEvent(ReminderEvent.Open(42))
            runCurrent()

            viewModel.onEvent(ReminderEvent.Choose(ReminderChoice.ThisEvening))
            viewModel.onEvent(ReminderEvent.Set("  Pack ID  "))
            runCurrent()

            val evening = Instant.parse("2026-10-18T20:00:00Z")
            assertEquals(listOf(MessageReminder(PRIVATE_CHAT, 42, evening, "Pack ID")), reminders.saved.value)
            assertNull(viewModel.sheet.value)
            assertEquals(listOf<ReminderEffect>(ReminderEffect.Set(UiText.Plain("8:00 PM"))), effects)

            viewModel.onEvent(ReminderEvent.Undo)
            runCurrent()
            assertTrue(reminders.saved.value.isEmpty())
        }

    @Test
    fun `a message with a reminder opens on its time and note, and undo puts the earlier one back`() =
        runTest {
            val friday = Instant.parse("2026-10-23T18:00:00Z")
            val earlier = MessageReminder(PRIVATE_CHAT, 42, friday, "Pack ID")
            reminders.saved.value = listOf(earlier)
            val viewModel = createViewModel()
            viewModel.onEvent(ReminderEvent.Open(42))
            runCurrent()

            val sheet = viewModel.sheet.value!!
            assertEquals(true, sheet.isExisting)
            assertEquals("Pack ID", sheet.note)
            assertEquals(ReminderChoice.Custom, sheet.chosen)
            assertEquals(day("Fri", "6:00 PM"), sheet.options.last().time)

            viewModel.onEvent(ReminderEvent.Choose(ReminderChoice.InOneHour))
            viewModel.onEvent(ReminderEvent.Set("Pack ID"))
            runCurrent()
            viewModel.onEvent(ReminderEvent.Undo)
            runCurrent()

            assertEquals(listOf(earlier), reminders.saved.value)
        }

    @Test
    fun `a picked moment rings then, and the choice survives process death`() =
        runTest {
            val handle = SavedStateHandle()
            val viewModel = createViewModel(handle)
            viewModel.onEvent(ReminderEvent.Open(42))
            runCurrent()
            val picked = Instant.parse("2026-10-30T07:15:00Z")

            viewModel.onEvent(ReminderEvent.Pick(picked.epochSecond))
            runCurrent()
            val restored = createViewModel(handle)
            runCurrent()

            assertEquals(ReminderChoice.Custom, restored.sheet.value!!.chosen)
            restored.onEvent(ReminderEvent.Set(""))
            runCurrent()
            assertEquals(
                picked,
                reminders.saved.value
                    .single()
                    .at,
            )
        }

    @Test
    fun `deleting removes the message's reminder`() =
        runTest {
            reminders.saved.value = listOf(MessageReminder(PRIVATE_CHAT, 42, Instant.parse("2026-10-23T18:00:00Z")))
            val viewModel = createViewModel()
            val effects = effectsOf(viewModel)
            viewModel.onEvent(ReminderEvent.Open(42))
            runCurrent()

            viewModel.onEvent(ReminderEvent.Delete)
            runCurrent()

            assertTrue(reminders.saved.value.isEmpty())
            assertEquals(listOf<ReminderEffect>(ReminderEffect.Deleted), effects)
        }

    @Test
    fun `a failed write keeps the sheet and says why`() =
        runTest {
            reminders.failure = Outcome.Failure(DomainError.Unknown(0, "disk"))
            val viewModel = createViewModel()
            val effects = effectsOf(viewModel)
            viewModel.onEvent(ReminderEvent.Open(42))
            runCurrent()

            viewModel.onEvent(ReminderEvent.Set(""))
            runCurrent()

            assertEquals(42L, viewModel.sheet.value?.messageId)
            assertTrue(effects.single() is ReminderEffect.ShowError)
        }

    @Test
    fun `the chips say when the chat's reminders not done yet ring, and follow them`() =
        runTest {
            reminders.saved.value =
                listOf(
                    MessageReminder(PRIVATE_CHAT, 42, Instant.parse("2026-10-18T20:00:00Z")),
                    MessageReminder(PRIVATE_CHAT, 43, Instant.parse("2026-10-19T09:00:00Z")),
                    MessageReminder(PRIVATE_CHAT, 44, Instant.parse("2026-10-19T09:00:00Z"), isDone = true),
                    MessageReminder(PRIVATE_CHAT + 1, 45, Instant.parse("2026-10-19T09:00:00Z")),
                )
            val viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.chips.collect {} }

            assertEquals(
                mapOf(42L to UiText.Plain("8:00 PM"), 43L to day("Mon", "9:00 AM")),
                viewModel.chips.value,
            )

            reminders.saved.value = reminders.saved.value.filterNot { it.messageId == 42L }
            runCurrent()
            assertEquals(mapOf(43L to day("Mon", "9:00 AM")), viewModel.chips.value)
        }
}
