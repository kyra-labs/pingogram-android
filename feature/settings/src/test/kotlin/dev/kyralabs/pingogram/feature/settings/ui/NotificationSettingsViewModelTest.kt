package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.repository.TdlibNotificationSettingsRepository
import dev.kyralabs.pingogram.core.domain.model.NotificationPrompt
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.domain.model.PushDelivery
import dev.kyralabs.pingogram.core.domain.model.ScopeNotificationSettings
import dev.kyralabs.pingogram.core.domain.repository.NotificationPromptRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeKeepConnectedRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {
    private val client = FakeTdlibClient()
    private val keepConnected = FakeKeepConnectedRepository()
    private val prompt = FakePromptRepository()

    /** What TDLib keeps for each scope, changed by setScopeNotificationSettings as TDLib would. */
    private val tdSettings =
        mutableMapOf(
            TdApi.NotificationSettingsScopePrivateChats::class.java to tdScope(muteFor = 0),
            TdApi.NotificationSettingsScopeGroupChats::class.java to tdScope(muteFor = Int.MAX_VALUE),
            TdApi.NotificationSettingsScopeChannelChats::class.java to tdScope(muteFor = 0, showPreview = false),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        client.on(
            TdApi.GetScopeNotificationSettings::class,
        ) { TdResult.Success(tdSettings.getValue(it.scope.javaClass)) }
        client.on(TdApi.SetScopeNotificationSettings::class) {
            tdSettings[it.scope.javaClass] = it.notificationSettings
            TdResult.Success(TdApi.Ok())
        }
        client.on(TdApi.ResetAllNotificationSettings::class, TdResult.Success(TdApi.Ok()))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun tdScope(
        muteFor: Int,
        showPreview: Boolean = true,
    ) = TdApi.ScopeNotificationSettings(muteFor, 0, showPreview, true, false, 0, true, false, false)

    // Through the real TDLib repository on FakeTdlibClient, as the app wires it.
    private fun TestScope.viewModel(pushDelivery: PushDelivery = PushDelivery.TdlibConnection) =
        NotificationSettingsViewModel(
            TdlibNotificationSettingsRepository(client, FakeWallClock(TEST_NOW)),
            keepConnected,
            prompt,
            pushDelivery,
        ).also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: NotificationSettingsViewModel): List<NotificationSettingsEffect> {
        val effects = mutableListOf<NotificationSettingsEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: NotificationSettingsViewModel,
        event: NotificationSettingsEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    private val NotificationSettingsViewModel.state get() = uiState.value

    private val NotificationSettingsViewModel.settings
        get() = (state.scopes as ScopeSettingsState.Content).settings

    private fun settings(
        isMuted: Boolean = false,
        showPreview: Boolean = true,
    ) = ScopeNotificationSettings(isMuted, showPreview, pinnedMessages = true, mentions = true)

    private fun access(
        enabled: Boolean = false,
        granted: Boolean = false,
        rationale: Boolean = false,
        blocked: Set<NotificationScope> = emptySet(),
    ) = NotificationSettingsEvent.AccessChecked(enabled, granted, rationale, blocked)

    // Loading

    @Test
    fun `is loading until the defaults are loaded`() =
        runTest {
            client.on(TdApi.GetScopeNotificationSettings::class) {
                CompletableDeferred<TdResult<TdApi.ScopeNotificationSettings>>().await()
            }

            assertEquals(ScopeSettingsState.Loading, viewModel().state.scopes)
        }

    @Test
    fun `shows the defaults of every scope`() =
        runTest {
            assertEquals(
                mapOf(
                    NotificationScope.PrivateChats to settings(),
                    NotificationScope.GroupChats to settings(isMuted = true),
                    NotificationScope.ChannelChats to settings(showPreview = false),
                ),
                viewModel().settings,
            )
        }

    @Test
    fun `a failed load shows why, and trying again loads`() =
        runTest {
            client.on(
                TdApi.GetScopeNotificationSettings::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 8")),
            )
            val viewModel = viewModel()

            assertEquals(ScopeSettingsState.Error(DomainError.FloodWait(8)), viewModel.state.scopes)

            client.on(
                TdApi.GetScopeNotificationSettings::class,
            ) { TdResult.Success(tdSettings.getValue(it.scope.javaClass)) }
            send(viewModel, NotificationSettingsEvent.Retry)

            assertEquals(settings(), viewModel.settings[NotificationScope.PrivateChats])
        }

    @Test
    fun `applies TDLib's changes of the defaults as they come`() =
        runTest {
            val viewModel = viewModel()

            client.emit(TdApi.UpdateScopeNotificationSettings(TdApi.NotificationSettingsScopeGroupChats(), tdScope(0)))
            runCurrent()

            assertEquals(settings(), viewModel.settings[NotificationScope.GroupChats])
        }

    // Changing the defaults

    @Test
    fun `a flipped switch shows at once and is saved`() =
        runTest {
            val viewModel = viewModel()

            send(
                viewModel,
                NotificationSettingsEvent.ChangeScope(NotificationScope.PrivateChats, settings(showPreview = false)),
            )

            assertEquals(settings(showPreview = false), viewModel.settings[NotificationScope.PrivateChats])
            val sent = client.sentOf<TdApi.SetScopeNotificationSettings>().single()
            assertInstanceOf(TdApi.NotificationSettingsScopePrivateChats::class.java, sent.scope)
            assertFalse(sent.notificationSettings.showPreview)
        }

    @Test
    fun `a change that cannot be saved flips back and says why`() =
        runTest {
            client.on(TdApi.SetScopeNotificationSettings::class, TdResult.Failure(TdError(500, "Request timeout")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, NotificationSettingsEvent.ChangeScope(NotificationScope.GroupChats, settings()))

            assertEquals(settings(isMuted = true), viewModel.settings[NotificationScope.GroupChats])
            assertEquals(listOf(NotificationSettingsEffect.ShowError(DomainError.Network)), effects)
        }

    // Android's side

    @Test
    fun `blocked notifications and channels show as Android reports them`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, access(enabled = false, blocked = setOf(NotificationScope.ChannelChats)))

            assertTrue(viewModel.state.notificationsBlocked)
            assertEquals(setOf(NotificationScope.ChannelChats), viewModel.state.blockedChannels)

            send(viewModel, access(enabled = true, granted = true))

            assertFalse(viewModel.state.notificationsBlocked)
            assertTrue(viewModel.state.blockedChannels.isEmpty())
        }

    @Test
    fun `turning notifications on asks for the permission first, and saves that it asked`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            send(viewModel, access())

            send(viewModel, NotificationSettingsEvent.TurnOnNotifications)

            assertEquals(listOf(NotificationSettingsEffect.RequestPermission), effects)
            assertTrue(prompt.state.value.wasRequested)
        }

    @Test
    fun `once Android shows its dialog no more, turning notifications on opens the app's notification settings`() =
        runTest {
            prompt.state.value = NotificationPrompt(wasRequested = true)
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            send(viewModel, access(rationale = false))

            send(viewModel, NotificationSettingsEvent.TurnOnNotifications)

            assertEquals(listOf(NotificationSettingsEffect.OpenAppNotificationSettings), effects)
        }

    @Test
    fun `with the permission granted but notifications off, turning them on opens the app's notification settings`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            send(viewModel, access(granted = true))

            send(viewModel, NotificationSettingsEvent.TurnOnNotifications)

            assertEquals(listOf(NotificationSettingsEffect.OpenAppNotificationSettings), effects)
        }

    @Test
    fun `sound and vibration opens the scope's channel`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, NotificationSettingsEvent.OpenChannelSettings(NotificationScope.GroupChats))

            assertEquals(listOf(NotificationSettingsEffect.OpenChannelSettings(NotificationScope.GroupChats)), effects)
        }

    // Keep connected

    @Test
    fun `keep connected shows the saved setting and saves changes`() =
        runTest {
            val viewModel = viewModel()

            assertTrue(viewModel.state.isKeepConnectedOffered)
            assertFalse(viewModel.state.keepConnected)

            send(viewModel, NotificationSettingsEvent.SetKeepConnected(true))

            assertTrue(keepConnected.keepConnected.value)
            assertTrue(viewModel.state.keepConnected)
        }

    @Test
    fun `keep connected that cannot be saved says why`() =
        runTest {
            keepConnected.failure = DomainError.Unknown(0, "Disk full")
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, NotificationSettingsEvent.SetKeepConnected(true))

            assertFalse(viewModel.state.keepConnected)
            assertEquals(listOf(NotificationSettingsEffect.ShowError(DomainError.Unknown(0, "Disk full"))), effects)
        }

    @Test
    fun `builds with push delivery offer no keep connected and never change it`() =
        runTest {
            val viewModel = viewModel(PushDelivery.Firebase)

            send(viewModel, NotificationSettingsEvent.SetKeepConnected(true))

            assertFalse(viewModel.state.isKeepConnectedOffered)
            assertFalse(keepConnected.keepConnected.value)
        }

    // Reset

    @Test
    fun `resetting asks first, then resets, says so and shows the new defaults`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, NotificationSettingsEvent.Reset)
            assertTrue(viewModel.state.isResetDialogOpen)
            assertTrue(client.sentOf<TdApi.ResetAllNotificationSettings>().isEmpty())

            tdSettings[TdApi.NotificationSettingsScopeGroupChats::class.java] = tdScope(muteFor = 0)
            send(viewModel, NotificationSettingsEvent.ConfirmReset)

            assertEquals(1, client.sentOf<TdApi.ResetAllNotificationSettings>().size)
            assertFalse(viewModel.state.isResetDialogOpen)
            assertFalse(viewModel.state.isResetting)
            assertEquals(listOf(NotificationSettingsEffect.ResetDone), effects)
            assertEquals(settings(), viewModel.settings[NotificationScope.GroupChats])
        }

    @Test
    fun `dismissing the confirmation resets nothing`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, NotificationSettingsEvent.Reset)
            send(viewModel, NotificationSettingsEvent.DismissReset)

            assertFalse(viewModel.state.isResetDialogOpen)
            assertTrue(client.sentOf<TdApi.ResetAllNotificationSettings>().isEmpty())
        }

    @Test
    fun `a failed reset says why`() =
        runTest {
            client.on(
                TdApi.ResetAllNotificationSettings::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 3")),
            )
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, NotificationSettingsEvent.ConfirmReset)

            assertFalse(viewModel.state.isResetting)
            assertEquals(listOf(NotificationSettingsEffect.ShowError(DomainError.FloodWait(3))), effects)
        }

    /** The chat list's notification request answers, in memory. */
    private class FakePromptRepository : NotificationPromptRepository {
        val state = MutableStateFlow(NotificationPrompt())

        override val prompt = state

        override suspend fun dismiss(): Outcome<Unit> {
            state.update { it.copy(isDismissed = true) }
            return Outcome.Success(Unit)
        }

        override suspend fun setRequested(): Outcome<Unit> {
            state.update { it.copy(wasRequested = true) }
            return Outcome.Success(Unit)
        }
    }
}
