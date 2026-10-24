package dev.kyralabs.pingogram.feature.chat.ui.scheduled

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSchedule
import dev.kyralabs.pingogram.core.domain.model.ScheduledMessage
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.outgoing
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.replyTo
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeMessageActionRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeMessageRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeScheduledMessageRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.chat.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeWallClock
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.OutgoingStatus
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * The list of scheduled messages of [ScheduledMessagesViewModel] (`REQUIREMENTS.md` FR-C5): the bubbles under their
 * send days, the states, each action with its errors, the edit, and what survives process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledMessagesViewModelTest {
    private val chats = FakeChatRepository()
    private val users = FakeUserRepository().apply { myUserId.value = ME }
    private val scheduled = FakeScheduledMessageRepository()
    private val messages = FakeMessageRepository()
    private val actions = FakeMessageActionRepository()
    private val clock = FakeWallClock(NOW)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        chats.setChats(chat(PRIVATE_CHAT, title = "Ada Lovelace"))
        users.setUsers(user(ADA, firstName = "Ada", lastName = "Lovelace"), user(ME, firstName = "Me"))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(savedState: SavedStateHandle = SavedStateHandle()): ScheduledMessagesViewModel {
        val viewModel =
            ScheduledMessagesViewModel(
                chatId = PRIVATE_CHAT,
                savedState = savedState,
                repository = scheduled,
                chatRepository = chats,
                userRepository = users,
                messageRepository = messages,
                actionRepository = actions,
                clock = clock,
                timePatterns = FakeTimePatterns(),
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: ScheduledMessagesViewModel): List<ScheduledEffect> {
        val effects = mutableListOf<ScheduledEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.collect(effects::add) }
        return effects
    }

    private val ScheduledMessagesViewModel.items: List<ChatItem>
        get() = (uiState.value.content as ScheduledContent.Content).items

    private val ScheduledMessagesViewModel.bubbles: List<ChatItem.Bubble>
        get() = items.filterIsInstance<ChatItem.Bubble>()

    /** Four messages: two today at 6:30 and 6:31 PM, one tomorrow at 8:50 AM that repeats daily, one when online. */
    private fun scheduleFour() {
        scheduled.setScheduled(
            PRIVATE_CHAT,
            at(11, TODAY_EVENING),
            at(12, TODAY_EVENING.plusSeconds(60)),
            at(13, TOMORROW_MORNING, repeatPeriod = DAY),
            ScheduledMessage(mine(14), MessageSchedule.WhenOnline),
        )
    }

    @Test
    fun `shows the messages as the conversation's bubbles under their send days, the latest at the bottom`() =
        runTest {
            scheduleFour()

            val viewModel = viewModel()

            val items = viewModel.items
            assertEquals(
                listOf<Any>(
                    14L,
                    "date:${Long.MAX_VALUE}",
                    13L,
                    "date:20745",
                    12L,
                    11L,
                    "date:20744",
                ),
                items.map {
                    it.key
                },
            )
            val ada = UiText.Plain("Ada Lovelace").bidiIsolated()
            assertEquals(UiText.resource(R.string.scheduled_when_user_online, ada), items[1].date)
            assertEquals(UiText.resource(R.string.chat_date_tomorrow), items[3].date)
            assertEquals(UiText.resource(R.string.chat_date_today), items[6].date)
            val bubbles = viewModel.bubbles
            val whenOnline = bubbles[0]
            val tomorrow = bubbles[1]
            val (second, first) = bubbles.drop(2)
            assertEquals(BubbleSchedule.WhenOnline(UiText.Plain("Ada Lovelace")), whenOnline.schedule)
            assertEquals(BubbleSchedule.At(DAY), tomorrow.schedule)
            assertEquals(listOf("8:50 AM", "6:31 PM", "6:30 PM"), listOf(tomorrow.time, second.time, first.time))
            assertEquals(listOf(GroupPosition.Last, GroupPosition.First), listOf(second.position, first.position))
            // Every bubble shows its send time, and no read state: nobody has them yet.
            assertTrue(viewModel.bubbles.all { it.showsTime && it.status == null })
            assertEquals(ComposerUiState.Hidden, viewModel.uiState.value.composer)
        }

    @Test
    fun `loads, then says when there are none`() =
        runTest {
            val viewModel = viewModel()

            assertEquals(ScheduledContent.Empty, viewModel.uiState.value.content)
            assertEquals(listOf(PRIVATE_CHAT), scheduled.collected)
        }

    @Test
    fun `a message that failed to reach Telegram shows it`() =
        runTest {
            val failed = DomainError.Network
            val message = mine(11).copy(sendingState = SendingState.Failed(failed, true, null))
            scheduled.setScheduled(
                PRIVATE_CHAT,
                ScheduledMessage(message, MessageSchedule.At(TODAY_EVENING.epochSecond)),
            )

            val viewModel = viewModel()

            assertEquals(OutgoingStatus.Failed, viewModel.bubbles.single().status)
            assertEquals(false, viewModel.bubbles.single().canResend)
        }

    @Test
    fun `a failed load shows its error, and Try again loads again`() =
        runTest {
            scheduled.loadError = Outcome.Failure(DomainError.FloodWait(30))
            val viewModel = viewModel()
            assertEquals(ScheduledContent.Error(DomainError.FloodWait(30)), viewModel.uiState.value.content)

            scheduled.loadError = null
            scheduleFour()
            viewModel.onEvent(ScheduledEvent.Retry)
            runCurrent()

            assertEquals(4, viewModel.bubbles.size)
            assertEquals(2, scheduled.collected.size)
        }

    @Test
    fun `messages Telegram sent leave the list by themselves, and once all are gone it says so`() =
        runTest {
            scheduleFour()
            val viewModel = viewModel()

            scheduled.setScheduled(PRIVATE_CHAT, at(13, TOMORROW_MORNING, repeatPeriod = DAY))
            runCurrent()
            assertEquals(listOf(13L), viewModel.bubbles.map { it.id })

            scheduled.setScheduled(PRIVATE_CHAT)
            runCurrent()
            assertEquals(ScheduledContent.Empty, viewModel.uiState.value.content)
        }

    @Test
    fun `the actions say when it goes out and offer what TDLib allows`() =
        runTest {
            scheduleFour()
            val viewModel = viewModel()

            viewModel.onEvent(ScheduledEvent.ShowActions(13))
            runCurrent()

            val sheet = viewModel.uiState.value.actions!!
            assertEquals(ScheduledAction.entries, sheet.actions)
            assertEquals(DAY, sheet.repeatPeriod)
            val dayTime =
                UiText.resource(R.string.scheduled_day_time, UiText.resource(R.string.chat_date_tomorrow), "8:50 AM")
            assertEquals(UiText.resource(R.string.scheduled_sends_at, dayTime), sheet.sendsAt)

            viewModel.onEvent(ScheduledEvent.DismissActions)
            runCurrent()
            assertNull(viewModel.uiState.value.actions)
        }

    @Test
    fun `a message that cannot be edited or saved offers neither`() =
        runTest {
            scheduleFour()
            actions.onProperties = {
                Outcome.Success(FakeMessageActionRepository.ALL_ALLOWED.copy(canBeEdited = false, canBeSaved = false))
            }
            val viewModel = viewModel()

            viewModel.onEvent(ScheduledEvent.ShowActions(14))
            runCurrent()

            val sheet = viewModel.uiState.value.actions!!
            assertEquals(
                listOf(ScheduledAction.SendNow, ScheduledAction.Reschedule, ScheduledAction.Delete),
                sheet.actions,
            )
            assertEquals(
                UiText.resource(R.string.scheduled_sends_when_user_online, UiText.Plain("Ada Lovelace").bidiIsolated()),
                sheet.sendsAt,
            )
        }

    @Test
    fun `sending now sends it and says so, or says why Telegram refused`() =
        runTest {
            scheduleFour()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(ScheduledEvent.Act(11, ScheduledAction.SendNow))
            runCurrent()
            scheduled.onCall = { Outcome.Failure(DomainError.FloodWait(30)) }
            viewModel.onEvent(ScheduledEvent.Act(12, ScheduledAction.SendNow))
            runCurrent()

            assertEquals(listOf("sendNow:$PRIVATE_CHAT:11", "sendNow:$PRIVATE_CHAT:12"), scheduled.calls)
            assertEquals(listOf(ScheduledEffect.Sent, ScheduledEffect.ShowError(DomainError.FloodWait(30))), effects)
        }

    @Test
    fun `rescheduling opens the pickers at its time, then moves it keeping its repeat period`() =
        runTest {
            scheduleFour()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(ScheduledEvent.Act(13, ScheduledAction.Reschedule))
            runCurrent()
            assertEquals(RescheduleDialog(13, TOMORROW_MORNING.epochSecond), viewModel.uiState.value.reschedule)

            val later = TOMORROW_MORNING.plus(Duration.ofHours(2)).epochSecond
            viewModel.onEvent(ScheduledEvent.Reschedule(13, later))
            runCurrent()

            assertNull(viewModel.uiState.value.reschedule)
            assertEquals(listOf("reschedule:$PRIVATE_CHAT:13:$later:$DAY"), scheduled.calls)
            assertEquals(listOf<ScheduledEffect>(ScheduledEffect.Rescheduled(later)), effects)
        }

    @Test
    fun `a refused reschedule says why, and dismissing the pickers changes nothing`() =
        runTest {
            scheduleFour()
            scheduled.onCall = { Outcome.Failure(DomainError.Network) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(ScheduledEvent.Act(11, ScheduledAction.Reschedule))
            viewModel.onEvent(ScheduledEvent.DismissReschedule)
            runCurrent()
            assertNull(viewModel.uiState.value.reschedule)

            viewModel.onEvent(ScheduledEvent.Reschedule(11, TOMORROW_MORNING.epochSecond))
            runCurrent()

            assertEquals(listOf<ScheduledEffect>(ScheduledEffect.ShowError(DomainError.Network)), effects)
        }

    @Test
    fun `deleting asks first, then deletes it and says so`() =
        runTest {
            scheduleFour()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(ScheduledEvent.Act(12, ScheduledAction.Delete))
            runCurrent()
            assertEquals(ScheduledDeleteDialog(12), viewModel.uiState.value.deleteDialog)
            assertEquals(emptyList<String>(), scheduled.calls)

            viewModel.onEvent(ScheduledEvent.ConfirmDelete)
            runCurrent()

            assertNull(viewModel.uiState.value.deleteDialog)
            assertEquals(listOf("delete:$PRIVATE_CHAT:12"), scheduled.calls)
            assertEquals(listOf<ScheduledEffect>(ScheduledEffect.Deleted), effects)
        }

    @Test
    fun `a refused deletion says why, and a dismissed one deletes nothing`() =
        runTest {
            scheduleFour()
            scheduled.onCall = { Outcome.Failure(DomainError.Unauthorized) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(ScheduledEvent.Act(12, ScheduledAction.Delete))
            viewModel.onEvent(ScheduledEvent.DismissDelete)
            runCurrent()
            assertEquals(emptyList<String>(), scheduled.calls)

            viewModel.onEvent(ScheduledEvent.Act(12, ScheduledAction.Delete))
            viewModel.onEvent(ScheduledEvent.ConfirmDelete)
            runCurrent()

            assertEquals(listOf<ScheduledEffect>(ScheduledEffect.ShowError(DomainError.Unauthorized)), effects)
        }

    @Test
    fun `copying puts its text on the clipboard`() =
        runTest {
            scheduleFour()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(ScheduledEvent.Act(11, ScheduledAction.Copy))
            runCurrent()

            assertEquals(listOf<ScheduledEffect>(ScheduledEffect.CopyText("Message 11")), effects)
        }

    @Test
    fun `an action on a message sent or deleted meanwhile says so`() =
        runTest {
            scheduleFour()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(ScheduledEvent.ShowActions(99))
            viewModel.onEvent(ScheduledEvent.Act(99, ScheduledAction.SendNow))
            runCurrent()

            assertEquals(listOf(ScheduledEffect.MessageGone, ScheduledEffect.MessageGone), effects)
            assertEquals(emptyList<String>(), scheduled.calls)
        }

    @Test
    fun `editing puts its text into the composer, and saving sends the edit`() =
        runTest {
            scheduleFour()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(ScheduledEvent.Act(11, ScheduledAction.Edit))
            runCurrent()

            val composer = viewModel.uiState.value.composer as ComposerUiState.Available
            assertEquals(11L, (composer.bar as ComposerBar.Edit).messageId)
            assertEquals(false, composer.canSchedule)
            assertEquals("Message 11", viewModel.composerText.content.text)
            assertEquals(listOf<ScheduledEffect>(ScheduledEffect.FocusComposer), effects)

            viewModel.composerText.setContent(text("Message 11, edited"))
            viewModel.onEvent(ScheduledEvent.SaveEdit)
            runCurrent()

            assertEquals(listOf("editText:$PRIVATE_CHAT:11"), actions.calls)
            assertEquals("Message 11, edited", actions.edits.single().text)
            assertEquals(ComposerUiState.Hidden, viewModel.uiState.value.composer)
            assertEquals("", viewModel.composerText.content.text)
        }

    @Test
    fun `an unchanged edit sends nothing, and closing the bar ends the edit`() =
        runTest {
            scheduleFour()
            val viewModel = viewModel()

            viewModel.onEvent(ScheduledEvent.Act(11, ScheduledAction.Edit))
            viewModel.onEvent(ScheduledEvent.SaveEdit)
            viewModel.onEvent(ScheduledEvent.Act(12, ScheduledAction.Edit))
            viewModel.onEvent(ScheduledEvent.CancelEdit)
            runCurrent()

            assertEquals(emptyList<String>(), actions.calls)
            assertEquals(ComposerUiState.Hidden, viewModel.uiState.value.composer)
        }

    @Test
    fun `a refused edit comes back into the composer with a snackbar`() =
        runTest {
            scheduleFour()
            actions.onAction = { Outcome.Failure(DomainError.FloodWait(12)) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(ScheduledEvent.Act(11, ScheduledAction.Edit))
            viewModel.composerText.setContent(text("Edited"))
            viewModel.onEvent(ScheduledEvent.SaveEdit)
            runCurrent()

            assertEquals("Edited", viewModel.composerText.content.text)
            assertTrue(viewModel.uiState.value.composer is ComposerUiState.Available)
            assertEquals(ScheduledEffect.ShowError(DomainError.FloodWait(12)), effects.last())
        }

    @Test
    fun `an edit survives process death with its text`() =
        runTest {
            scheduleFour()
            val savedState =
                SavedStateHandle(
                    mapOf(
                        ScheduledMessagesViewModel.KEY_EDITING to 12L,
                        ScheduledMessagesViewModel.KEY_TEXT to "Half edited",
                    ),
                )

            val viewModel = viewModel(savedState)

            val composer = viewModel.uiState.value.composer as ComposerUiState.Available
            assertEquals(12L, composer.bar?.messageId)
            assertEquals("Half edited", viewModel.composerText.content.text)
        }

    @Test
    fun `the messages scheduled replies are to are fetched once each`() =
        runTest {
            val reply = mine(11).copy(replyTo = replyTo(5))
            scheduled.setScheduled(PRIVATE_CHAT, ScheduledMessage(reply, MessageSchedule.At(TODAY_EVENING.epochSecond)))
            messages.onRepliedMessage = { _, _ -> Outcome.Failure(DomainError.NotFound) }

            val viewModel = viewModel()
            scheduled.setScheduled(
                PRIVATE_CHAT,
                ScheduledMessage(reply, MessageSchedule.At(TOMORROW_MORNING.epochSecond)),
            )
            runCurrent()

            assertEquals(listOf(PRIVATE_CHAT to 11L), messages.repliedRequests)
            assertEquals(1, viewModel.bubbles.size)
        }

    private fun mine(id: Long) =
        outgoing(id, date = Instant.EPOCH).copy(content = MessageContent.Text(text("Message $id"), linkPreview = null))

    private fun at(
        id: Long,
        time: Instant,
        repeatPeriod: Int = 0,
    ) = ScheduledMessage(mine(id), MessageSchedule.At(time.epochSecond, repeatPeriod))

    private companion object {
        /** 2026-10-18 (epoch day 20744), 6:30 PM UTC, NOW's day. */
        val TODAY_EVENING: Instant = Instant.parse("2026-10-18T18:30:00Z")
        val TOMORROW_MORNING: Instant = Instant.parse("2026-10-19T08:50:00Z")
        const val DAY = 86_400
    }
}
