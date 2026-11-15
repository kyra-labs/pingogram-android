package dev.kyralabs.pingogram.feature.settings.ui.reminders

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.SecretChatLinkPreviews
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibMessageRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeLinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeReminderRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.settings.ui.FakeWallClock
import dev.kyralabs.pingogram.feature.settings.ui.TEST_NOW
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Locale

/**
 * The Reminders page (the owner's board 29e), through the real chat, message and user repositories on a
 * [FakeTdlibClient]: upcoming and done, the messages' previews, sorting, marking done, deleting and undoing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModelTest {
    private val client = FakeTdlibClient()
    private val reminders = FakeReminderRepository()

    // TEST_NOW is Sunday, October 18, 2026, 9:41:30 AM UTC.
    private val tomorrow = Instant.parse("2026-10-19T09:00:00Z")
    private val friday = Instant.parse("2026-10-23T18:00:00Z")
    private val past = Instant.parse("2026-10-18T08:00:00Z")

    private val ada = TdApiFixtures.chat(id = 5, type = TdApi.ChatTypePrivate(5), title = "Ada Lovelace")
    private val trip = TdApiFixtures.chat(id = -100, type = TdApi.ChatTypeBasicGroup(1), title = "Trip")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        client.on(TdApi.GetMessage::class) { request ->
            if (request.messageId == GONE) {
                TdResult.Failure(TdError(404, "Not Found"))
            } else {
                TdResult.Success(
                    TdApiFixtures.message(
                        id = request.messageId,
                        chatId = request.chatId,
                        content = TdApiFixtures.messageText("Message ${request.messageId}"),
                    ),
                )
            }
        }
        client.on(TdApi.GetUser::class, TdResult.Success(TdApiFixtures.user(id = 42, firstName = "Aisha")))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): RemindersViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val chats = TdlibChatRepository(client, backgroundScope, dispatcher, OpenChats())
        val users = TdlibUserRepository(client, backgroundScope)
        val messages =
            TdlibMessageRepository(
                client,
                backgroundScope,
                SecretChatLinkPreviews(client, FakeLinkPreviewSettingsRepository()),
            )
        runCurrent()
        listOf(ada, trip).forEach { client.emit(TdApiFixtures.updateNewChat(it)) }
        client.emit(TdApi.UpdateUser(TdApiFixtures.user(id = 42, firstName = "Aisha")))
        runCurrent()
        return RemindersViewModel(
            savedStateHandle = handle,
            reminders = reminders,
            chats = chats,
            messages = messages,
            users = users,
            clock = FakeWallClock(TEST_NOW),
            timePatterns = FakeTimePatterns(),
            locale = { Locale.US },
        ).also {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
            runCurrent()
        }
    }

    private val RemindersViewModel.content get() = uiState.value as RemindersUiState.Content

    @Test
    fun `no reminders shows the empty state`() =
        runTest {
            assertEquals(RemindersUiState.Empty, viewModel().uiState.value)
        }

    @Test
    fun `upcoming ones come before done ones, with their chats, times, previews and notes`() =
        runTest {
            reminders.saved.value =
                listOf(
                    MessageReminder(5, 1, friday, note = "Upload the scans"),
                    MessageReminder(-100, 2, tomorrow),
                    MessageReminder(5, 3, past, isDone = true),
                )
            val viewModel = viewModel()

            val upcoming = viewModel.content.upcoming
            assertEquals(listOf(2L, 1L), upcoming.map { it.messageId })
            with(upcoming.first()) {
                assertEquals("Trip", chatTitle)
                assertEquals(AvatarShape.Group, avatarShape)
                assertEquals(
                    UiText.resource(
                        R.string.reminders_preview_sender,
                        UiText.Plain("Aisha"),
                        UiText.Plain("Message 2"),
                    ),
                    preview,
                )
            }
            with(upcoming.last()) {
                assertEquals(AvatarShape.Circle, avatarShape)
                assertEquals(UiText.Plain("Message 1"), preview)
                assertEquals("Upload the scans", note)
            }
            assertEquals(listOf(3L), viewModel.content.done.map { it.messageId })
        }

    @Test
    fun `a reminder whose time went by is overdue, and a message TDLib no longer has has no preview`() =
        runTest {
            reminders.saved.value = listOf(MessageReminder(5, GONE, past))
            val row = viewModel().content.upcoming.single()

            assertTrue(row.isOverdue)
            assertEquals(null, row.preview)
        }

    @Test
    fun `by chat sorts by the chat's name, and the choice survives process death`() =
        runTest {
            reminders.saved.value = listOf(MessageReminder(-100, 2, tomorrow), MessageReminder(5, 1, friday))
            val handle = SavedStateHandle()
            val viewModel = viewModel(handle)

            viewModel.onEvent(RemindersEvent.Sort(ReminderSort.ByChat))
            runCurrent()
            assertEquals(listOf("Ada Lovelace", "Trip"), viewModel.content.upcoming.map { it.chatTitle })

            val restored = viewModel(handle)
            assertEquals(ReminderSort.ByChat, restored.content.sort)
        }

    @Test
    fun `marking done moves it to done, and back`() =
        runTest {
            reminders.saved.value = listOf(MessageReminder(5, 1, friday))
            val viewModel = viewModel()

            viewModel.onEvent(RemindersEvent.ToggleDone(5, 1))
            runCurrent()
            assertEquals(1, viewModel.content.done.size)

            viewModel.onEvent(RemindersEvent.ToggleDone(5, 1))
            runCurrent()
            assertEquals(1, viewModel.content.upcoming.size)
        }

    @Test
    fun `deleting removes it, and undo puts it back`() =
        runTest {
            val reminder = MessageReminder(5, 1, friday, note = "Upload the scans")
            reminders.saved.value = listOf(reminder)
            val viewModel = viewModel()
            val effects = mutableListOf<RemindersEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            viewModel.onEvent(RemindersEvent.Delete(5, 1))
            runCurrent()
            assertEquals(RemindersUiState.Empty, viewModel.uiState.value)
            assertEquals(listOf<RemindersEffect>(RemindersEffect.Deleted), effects)

            viewModel.onEvent(RemindersEvent.UndoDelete)
            runCurrent()
            assertEquals(listOf(reminder), reminders.saved.value)
        }

    @Test
    fun `a failed write says why`() =
        runTest {
            reminders.saved.value = listOf(MessageReminder(5, 1, friday))
            val viewModel = viewModel()
            val effects = mutableListOf<RemindersEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
            reminders.failure = Outcome.Failure(DomainError.Unknown(0, "disk"))

            viewModel.onEvent(RemindersEvent.ToggleDone(5, 1))
            runCurrent()

            assertTrue(effects.single() is RemindersEffect.ShowError)
        }

    @Test
    fun `opening a reminder opens its chat`() =
        runTest {
            reminders.saved.value = listOf(MessageReminder(5, 1, friday))
            val viewModel = viewModel()
            val effects = mutableListOf<RemindersEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            viewModel.onEvent(RemindersEvent.Open(5))
            runCurrent()

            assertEquals(listOf<RemindersEffect>(RemindersEffect.OpenChat(5)), effects)
        }

    private companion object {
        const val GONE = 99L
    }
}
