package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibNotificationSettingsRepository
import dev.kyralabs.pingogram.core.domain.model.ChatMute
import dev.kyralabs.pingogram.core.domain.model.MuteChange
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chatNotificationSettings
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationExceptionsViewModelTest {
    private val client = FakeTdlibClient()
    private val now = TEST_NOW.epochSecond

    /** Muted forever, muted for an hour more (until 10:41 AM), and unmuted. */
    private val chats =
        mutableMapOf(
            1L to chat(id = 1, title = "Engines", notificationSettings = own(Int.MAX_VALUE)),
            2L to chat(id = 2, title = "Looms", notificationSettings = own(3_600)),
            3L to chat(id = 3, title = "Royal Society", notificationSettings = own(0)),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        client.on(TdApi.GetChatNotificationSettingsExceptions::class) {
            TdResult.Success(TdApi.Chats(chats.size, chats.keys.toLongArray()))
        }
        client.on(TdApi.GetChat::class) { TdResult.Success(chats.getValue(it.chatId)) }
        client.on(TdApi.SetChatNotificationSettings::class, TdResult.Success(TdApi.Ok()))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun own(muteFor: Int) = chatNotificationSettings(useDefaultMuteFor = false, muteFor = muteFor)

    // Through the real TDLib repository on FakeTdlibClient, as the app wires it.
    private fun TestScope.viewModel() =
        NotificationExceptionsViewModel(
            NotificationScope.GroupChats,
            TdlibNotificationSettingsRepository(client, FakeWallClock(TEST_NOW)),
            testTimeFormats(),
        ).also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: NotificationExceptionsViewModel): List<NotificationExceptionsEffect> {
        val effects = mutableListOf<NotificationExceptionsEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: NotificationExceptionsViewModel,
        event: NotificationExceptionsEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    private val NotificationExceptionsViewModel.state get() = uiState.value

    /** When a mute ends, as the page says it ("10:41 AM", with the space the JDK puts before AM). */
    private fun until(epochSeconds: Long) = testTimeFormats().formatter().until(epochSeconds, TEST_NOW)

    private val NotificationExceptionsViewModel.items
        get() = (state.exceptions as ExceptionsState.Content).items

    @Test
    fun `is loading until the list is loaded`() =
        runTest {
            client.on(
                TdApi.GetChatNotificationSettingsExceptions::class,
            ) { CompletableDeferred<TdResult<TdApi.Chats>>().await() }

            assertEquals(ExceptionsState.Loading, viewModel().state.exceptions)
        }

    @Test
    fun `lists the scope's exceptions with whether they notify`() =
        runTest {
            val viewModel = viewModel()

            assertEquals(
                listOf(
                    Triple(1L, "Engines", UiText.resource(R.string.settings_exception_muted)),
                    Triple(2L, "Looms", UiText.resource(R.string.settings_exception_muted_until, until(now + 3_600))),
                    Triple(3L, "Royal Society", UiText.resource(R.string.settings_exception_unmuted)),
                ),
                viewModel.items.map { Triple(it.chatId, it.title, it.status) },
            )
            assertInstanceOf(
                TdApi.NotificationSettingsScopeGroupChats::class.java,
                client.sentOf<TdApi.GetChatNotificationSettingsExceptions>().single().scope,
            )
        }

    @Test
    fun `without exceptions the page is empty`() =
        runTest {
            chats.clear()

            assertEquals(ExceptionsState.Empty, viewModel().state.exceptions)
        }

    @Test
    fun `a failed load shows why, and trying again loads`() =
        runTest {
            client.on(
                TdApi.GetChatNotificationSettingsExceptions::class,
                TdResult.Failure(TdError(500, "Request timeout")),
            )
            val viewModel = viewModel()

            assertEquals(ExceptionsState.Error(DomainError.Network), viewModel.state.exceptions)

            client.on(TdApi.GetChatNotificationSettingsExceptions::class) {
                TdResult.Success(TdApi.Chats(chats.size, chats.keys.toLongArray()))
            }
            send(viewModel, NotificationExceptionsEvent.Retry)

            assertEquals(3, viewModel.items.size)
        }

    @Test
    fun `a row opens its mute options, and choosing one saves it and closes them`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, NotificationExceptionsEvent.OpenMuteOptions(3))
            assertEquals(3L, viewModel.state.muteOptions?.chatId)

            send(viewModel, NotificationExceptionsEvent.Change(3, MuteChange.MuteFor(8 * 3_600)))

            assertNull(viewModel.state.muteOptions)
            val sent = client.sentOf<TdApi.SetChatNotificationSettings>().single()
            assertEquals(3L to 8 * 3_600, sent.chatId to sent.notificationSettings.muteFor)
            val item = viewModel.items.single { it.chatId == 3L }
            assertEquals(ChatMute.MutedUntil(now + 8 * 3_600), item.mute)
            assertEquals(UiText.resource(R.string.settings_exception_muted_until, until(now + 8 * 3_600)), item.status)
        }

    @Test
    fun `removing an exception makes the chat use the default and takes it off the list`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, NotificationExceptionsEvent.Change(1, MuteChange.UseDefault))

            assertEquals(
                true,
                client
                    .sentOf<TdApi.SetChatNotificationSettings>()
                    .single()
                    .notificationSettings.useDefaultMuteFor,
            )
            assertEquals(listOf(2L, 3L), viewModel.items.map { it.chatId })
        }

    @Test
    fun `the last exception removed leaves the page empty`() =
        runTest {
            chats.keys.retainAll(setOf(1L))
            val viewModel = viewModel()

            send(viewModel, NotificationExceptionsEvent.Change(1, MuteChange.UseDefault))

            assertEquals(ExceptionsState.Empty, viewModel.state.exceptions)
        }

    @Test
    fun `a change that fails keeps the row as it was and says why`() =
        runTest {
            client.on(
                TdApi.SetChatNotificationSettings::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 4")),
            )
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, NotificationExceptionsEvent.Change(3, MuteChange.MuteForever))

            val item = viewModel.items.single { it.chatId == 3L }
            assertEquals(ChatMute.Unmuted to false, item.mute to item.isChanging)
            assertEquals(listOf(NotificationExceptionsEffect.ShowError(DomainError.FloodWait(4))), effects)
        }

    @Test
    fun `a change shows progress until TDLib answers`() =
        runTest {
            val answer = CompletableDeferred<TdResult<TdApi.Ok>>()
            client.on(TdApi.SetChatNotificationSettings::class) { answer.await() }
            val viewModel = viewModel()

            send(viewModel, NotificationExceptionsEvent.Change(3, MuteChange.MuteForever))
            assertEquals(true, viewModel.items.single { it.chatId == 3L }.isChanging)

            answer.complete(TdResult.Success(TdApi.Ok()))
            runCurrent()

            val item = viewModel.items.single { it.chatId == 3L }
            assertEquals(ChatMute.MutedForever to false, item.mute to item.isChanging)
        }

    @Test
    fun `TDLib's changes of the chats apply as they come`() =
        runTest {
            val viewModel = viewModel()

            client.emit(TdApi.UpdateChatNotificationSettings(2, own(0)))
            client.emit(TdApi.UpdateChatNotificationSettings(1, chatNotificationSettings(useDefaultMuteFor = true)))
            runCurrent()

            assertEquals(
                listOf(2L to ChatMute.Unmuted, 3L to ChatMute.Unmuted),
                viewModel.items.map {
                    it.chatId to
                        it.mute
                },
            )
        }
}
