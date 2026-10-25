package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibAuthRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSessionRepository
import dev.kyralabs.pingogram.core.domain.model.SessionDevice
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibServers
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSessionsViewModelTest {
    private val client = FakeTdlibClient()
    private val savedState = SavedStateHandle()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Through the real TDLib repository on FakeTdlibClient, as the app wires it.
    private fun TestScope.viewModel(handle: SavedStateHandle = savedState): ActiveSessionsViewModel =
        ActiveSessionsViewModel(
            handle,
            TdlibSessionRepository(client),
            testTimeFormats(),
            TdlibAuthRepository(client, StandardTestDispatcher(testScheduler)),
        ).also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: ActiveSessionsViewModel): List<ActiveSessionsEffect> {
        val effects = mutableListOf<ActiveSessionsEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: ActiveSessionsViewModel,
        vararg events: ActiveSessionsEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    private fun session(
        id: Long,
        isCurrent: Boolean = false,
        isUnconfirmed: Boolean = false,
        isPasswordPending: Boolean = false,
        deviceType: TdApi.SessionDeviceType = TdApi.SessionDeviceTypeAndroid(),
        applicationName: String = "Pingogram",
        applicationVersion: String = "Pingogram 0.3.0",
        deviceModel: String = "Google Pixel 8",
        location: String = "Berlin, Germany",
        lastActive: String = "2026-10-18T08:05:00Z",
    ) = TdApi.Session(
        id,
        isCurrent,
        isPasswordPending,
        isUnconfirmed,
        true,
        true,
        deviceType,
        1,
        applicationName,
        applicationVersion,
        false,
        deviceModel,
        "Android",
        "15",
        1_700_000_000,
        Instant.parse(lastActive).epochSecond.toInt(),
        "203.0.113.7",
        location,
    )

    private fun answerSessions(vararg sessions: TdApi.Session) =
        client.on(TdApi.GetActiveSessions::class, TdResult.Success(TdApi.Sessions(arrayOf(*sessions), 180)))

    private fun answerTypical() =
        answerSessions(
            session(1, isCurrent = true),
            session(2, isUnconfirmed = true, deviceType = TdApi.SessionDeviceTypeChrome(), deviceModel = "Chrome 129"),
            session(3, deviceType = TdApi.SessionDeviceTypeMac(), deviceModel = "MacBook Pro"),
        )

    /** "[first] · [second]" as the view model joins them: each isolated (`bidiIsolated`). */
    private fun parts(
        first: UiText,
        second: UiText,
    ) = UiText.resource(R.string.settings_session_parts, first.bidiIsolated(), second.bidiIsolated())

    private val ActiveSessionsViewModel.state get() = uiState.value

    private val ActiveSessionsViewModel.loaded get() = state.sessions as SessionsState.Loaded

    private val ActiveSessionsViewModel.otherIds get() = loaded.others.map { it.id }

    @Test
    fun `is loading until TDLib reports the sessions`() =
        runTest {
            client.on(TdApi.GetActiveSessions::class) { CompletableDeferred<TdResult<TdApi.Sessions>>().await() }

            assertEquals(ActiveSessionsUiState(), viewModel().state)
        }

    @Test
    fun `shows this device, then new logins, then the others`() =
        runTest {
            answerTypical()

            val loaded = viewModel().loaded

            assertEquals(1L, loaded.current?.id)
            assertEquals(listOf(2L), loaded.unconfirmed.map { it.id })
            assertEquals(listOf(3L), loaded.others.map { it.id })
            assertEquals(180, loaded.inactiveSessionTtlDays)
        }

    @Test
    fun `says when this session is on Telegram's test servers`() =
        runTest {
            answerTypical()
            val viewModel = viewModel()
            assertFalse(viewModel.state.isOnTestServers)

            client.servers.value = TdlibServers.Test
            runCurrent()

            assertTrue(viewModel.state.isOnTestServers)
        }

    @Test
    fun `this device shows as online, the others with when they were last active`() =
        runTest {
            answerSessions(
                session(1, isCurrent = true),
                session(
                    3,
                    applicationName = "Telegram Desktop",
                    applicationVersion = "5.6",
                    lastActive = "2026-10-12T10:00:00Z",
                ),
            )

            val loaded = viewModel().loaded

            assertEquals(
                SessionItem(
                    id = 1,
                    device = SessionDevice.Phone,
                    title = UiText.Plain("Google Pixel 8"),
                    app = parts(UiText.Plain("Pingogram 0.3.0"), UiText.Plain("Android 15")),
                    activity =
                        parts(
                            UiText.Plain("Berlin, Germany"),
                            UiText.resource(R.string.settings_session_online),
                        ),
                ),
                loaded.current,
            )
            assertEquals(
                persistentListOf(
                    SessionItem(
                        id = 3,
                        device = SessionDevice.Phone,
                        title = UiText.Plain("Google Pixel 8"),
                        app = parts(UiText.Plain("Telegram Desktop 5.6"), UiText.Plain("Android 15")),
                        activity = parts(UiText.Plain("Berlin, Germany"), UiText.Plain("Mon")),
                    ),
                ),
                loaded.others,
            )
        }

    @Test
    fun `a Hebrew location stays isolated before the time`() =
        runTest {
            answerSessions(session(3, location = "תל אביב, ישראל"))

            val activity =
                viewModel()
                    .loaded.others
                    .single()
                    .activity as UiText.Resource

            // Its own direction in first-strong isolates: it never swaps places with the English time after it.
            assertEquals(R.string.settings_session_parts, activity.id)
            assertEquals(UiText.Plain("\u2068תל אביב, ישראל\u2069"), activity.args.first())
        }

    @Test
    fun `a session without a device model or location says so briefly`() =
        runTest {
            answerSessions(session(3, deviceModel = "", location = "", isPasswordPending = true))

            val item = viewModel().loaded.others.single()

            assertEquals(UiText.resource(R.string.settings_session_unknown_device), item.title)
            assertEquals("8:05 AM", plainSpaces((item.activity as UiText.Plain).text))
            assertTrue(item.isPasswordPending)
        }

    @Test
    fun `a failure shows why, and retry loads again`() =
        runTest {
            client.on(
                TdApi.GetActiveSessions::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 4")),
            )
            val viewModel = viewModel()

            assertEquals(SessionsState.Failed(DomainError.FloodWait(4)), viewModel.state.sessions)

            answerTypical()
            send(viewModel, ActiveSessionsEvent.Retry)

            assertEquals(listOf(3L), viewModel.otherIds)
        }

    @Test
    fun `no answer in time is a network error`() =
        runTest {
            client.on(TdApi.GetActiveSessions::class) { CompletableDeferred<TdResult<TdApi.Sessions>>().await() }
            val viewModel = viewModel()

            advanceTimeBy(REQUEST_TIMEOUT)
            runCurrent()

            assertEquals(SessionsState.Failed(DomainError.Network), viewModel.state.sessions)
        }

    @Test
    fun `showing again loads the sessions again, keeping what shows meanwhile and if that fails`() =
        runTest {
            answerTypical()
            val viewModel = viewModel()
            client.on(TdApi.GetActiveSessions::class, TdResult.Failure(TdError(500, "Network is unreachable")))

            send(viewModel, ActiveSessionsEvent.Refresh)

            assertEquals(listOf(3L), viewModel.otherIds)

            answerSessions(session(1, isCurrent = true))
            send(viewModel, ActiveSessionsEvent.Refresh)

            assertEquals(emptyList<Long>(), viewModel.otherIds)
            assertEquals(3, client.sentOf<TdApi.GetActiveSessions>().size)
        }

    @Test
    fun `showing again while loading loads once`() =
        runTest {
            client.on(TdApi.GetActiveSessions::class) { CompletableDeferred<TdResult<TdApi.Sessions>>().await() }
            val viewModel = viewModel()

            send(viewModel, ActiveSessionsEvent.Refresh)

            assertEquals(1, client.sentOf<TdApi.GetActiveSessions>().size)
        }

    // Terminating

    @Test
    fun `terminating asks for confirmation first`() =
        runTest {
            answerTypical()
            val viewModel = viewModel()
            val session = viewModel.loaded.others.single()

            send(viewModel, ActiveSessionsEvent.Terminate(session))

            assertEquals(TerminateDialog.Session(3, UiText.Plain("MacBook Pro")), viewModel.state.dialog)
            assertTrue(client.sentOf<TdApi.TerminateSession>().isEmpty())
        }

    @Test
    fun `dismissing the confirmation terminates nothing`() =
        runTest {
            answerTypical()
            val viewModel = viewModel()

            send(
                viewModel,
                ActiveSessionsEvent.Terminate(viewModel.loaded.others.single()),
                ActiveSessionsEvent.DismissTermination,
            )

            assertNull(viewModel.state.dialog)
            assertTrue(client.sentOf<TdApi.TerminateSession>().isEmpty())
        }

    @Test
    fun `a confirmed termination takes the session out of the list`() =
        runTest {
            answerTypical()
            client.on(TdApi.TerminateSession::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(
                viewModel,
                ActiveSessionsEvent.Terminate(viewModel.loaded.others.single()),
                ActiveSessionsEvent.ConfirmTermination,
            )

            assertEquals(3L, client.sentOf<TdApi.TerminateSession>().single().sessionId)
            assertEquals(emptyList<Long>(), viewModel.otherIds)
            assertNull(viewModel.state.dialog)
            assertEquals(listOf(ActiveSessionsEffect.SessionTerminated), effects)
        }

    @Test
    fun `a session shows progress while it is terminated`() =
        runTest {
            answerTypical()
            client.on(TdApi.TerminateSession::class) { CompletableDeferred<TdResult<TdApi.Ok>>().await() }
            val viewModel = viewModel()

            send(
                viewModel,
                ActiveSessionsEvent.Terminate(viewModel.loaded.unconfirmed.single()),
                ActiveSessionsEvent.ConfirmTermination,
            )

            assertEquals(setOf(2L), viewModel.state.busySessionIds)
        }

    @Test
    fun `a termination Telegram refuses for a recent login says so, and the session stays`() =
        runTest {
            answerTypical()
            client.on(
                TdApi.TerminateSession::class,
                TdResult.Failure(TdError(406, "FRESH_RESET_AUTHORISATION_FORBIDDEN")),
            )
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(
                viewModel,
                ActiveSessionsEvent.Terminate(viewModel.loaded.others.single()),
                ActiveSessionsEvent.ConfirmTermination,
            )

            assertEquals(listOf(ActiveSessionsEffect.TerminationRefused), effects)
            assertEquals(listOf(3L), viewModel.otherIds)
            assertTrue(viewModel.state.busySessionIds.isEmpty())
        }

    @Test
    fun `other termination failures show the error`() =
        runTest {
            answerTypical()
            client.on(TdApi.TerminateSession::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 8")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(
                viewModel,
                ActiveSessionsEvent.Terminate(viewModel.loaded.others.single()),
                ActiveSessionsEvent.ConfirmTermination,
            )

            assertEquals(listOf(ActiveSessionsEffect.ShowError(DomainError.FloodWait(8))), effects)
        }

    @Test
    fun `terminating all other sessions asks first, then leaves this device alone`() =
        runTest {
            answerTypical()
            client.on(TdApi.TerminateAllOtherSessions::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ActiveSessionsEvent.TerminateAllOthers)

            assertEquals(TerminateDialog.AllOthers, viewModel.state.dialog)
            assertTrue(client.sentOf<TdApi.TerminateAllOtherSessions>().isEmpty())

            send(viewModel, ActiveSessionsEvent.ConfirmTermination)

            assertEquals(1, client.sentOf<TdApi.TerminateAllOtherSessions>().size)
            assertEquals(1L, viewModel.loaded.current?.id)
            assertTrue(viewModel.loaded.unconfirmed.isEmpty() && viewModel.loaded.others.isEmpty())
            assertEquals(listOf(ActiveSessionsEffect.OtherSessionsTerminated), effects)
        }

    @Test
    fun `terminating all shows progress until TDLib answers, and runs once`() =
        runTest {
            answerTypical()
            client.on(TdApi.TerminateAllOtherSessions::class) { CompletableDeferred<TdResult<TdApi.Ok>>().await() }
            val viewModel = viewModel()

            send(viewModel, ActiveSessionsEvent.TerminateAllOthers, ActiveSessionsEvent.ConfirmTermination)
            send(viewModel, ActiveSessionsEvent.TerminateAllOthers, ActiveSessionsEvent.ConfirmTermination)

            assertTrue(viewModel.state.isTerminatingAll)
            assertEquals(1, client.sentOf<TdApi.TerminateAllOtherSessions>().size)
        }

    @Test
    fun `terminating all that Telegram refuses says so`() =
        runTest {
            answerTypical()
            client.on(
                TdApi.TerminateAllOtherSessions::class,
                TdResult.Failure(TdError(406, "FRESH_RESET_AUTHORISATION_FORBIDDEN")),
            )
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ActiveSessionsEvent.TerminateAllOthers, ActiveSessionsEvent.ConfirmTermination)

            assertEquals(listOf(ActiveSessionsEffect.TerminationRefused), effects)
            assertFalse(viewModel.state.isTerminatingAll)
            assertEquals(listOf(3L), viewModel.otherIds)
        }

    // Confirming a new login

    @Test
    fun `confirming a new login tells Telegram and loads the list again`() =
        runTest {
            answerTypical()
            client.on(TdApi.ConfirmSession::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            answerSessions(session(1, isCurrent = true), session(2), session(3))

            send(viewModel, ActiveSessionsEvent.ConfirmLogin(2))

            assertEquals(2L, client.sentOf<TdApi.ConfirmSession>().single().sessionId)
            assertEquals(listOf(ActiveSessionsEffect.LoginConfirmed), effects)
            assertEquals(listOf(2L, 3L), viewModel.otherIds)
            assertTrue(viewModel.loaded.unconfirmed.isEmpty())
        }

    @Test
    fun `a failed confirmation shows the error`() =
        runTest {
            answerTypical()
            client.on(TdApi.ConfirmSession::class, TdResult.Failure(TdError(500, "Network is unreachable")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ActiveSessionsEvent.ConfirmLogin(2))

            assertEquals(listOf(ActiveSessionsEffect.ShowError(DomainError.Network)), effects)
            assertEquals(listOf(2L), viewModel.loaded.unconfirmed.map { it.id })
        }

    // Process death

    @Test
    fun `the open confirmation survives process death`() =
        runTest {
            answerTypical()
            val viewModel = viewModel()
            send(viewModel, ActiveSessionsEvent.Terminate(viewModel.loaded.others.single()))

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertEquals(TerminateDialog.Session(3, UiText.Plain("MacBook Pro")), restored.state.dialog)
        }

    @Test
    fun `the confirmation to terminate all survives process death`() =
        runTest {
            answerTypical()
            send(viewModel(), ActiveSessionsEvent.TerminateAllOthers)

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertEquals(TerminateDialog.AllOthers, restored.state.dialog)
        }

    @Test
    fun `a dismissed confirmation is not restored`() =
        runTest {
            answerTypical()
            send(viewModel(), ActiveSessionsEvent.TerminateAllOthers, ActiveSessionsEvent.DismissTermination)

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertNull(restored.state.dialog)
        }

    private companion object {
        /** `REQUEST_TIMEOUT` of `core:data`. */
        val REQUEST_TIMEOUT = 30.seconds
    }
}
