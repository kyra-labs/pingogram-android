package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibAuthRepository
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val client = FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready)
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
    private fun TestScope.viewModel(handle: SavedStateHandle = savedState): SettingsViewModel =
        SettingsViewModel(handle, TdlibAuthRepository(client, StandardTestDispatcher(testScheduler)))
            .also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: SettingsViewModel): List<SettingsEffect> {
        val effects = mutableListOf<SettingsEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: SettingsViewModel,
        vararg events: SettingsEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    private fun answerLogOut(result: TdResult<TdApi.Ok>) = client.on(TdApi.LogOut::class, result)

    private fun failure(
        code: Int,
        message: String,
    ) = TdResult.Failure(TdError(code, message))

    private val SettingsViewModel.state get() = uiState.value

    @Test
    fun `starts with neither the confirmation nor progress`() =
        runTest {
            assertEquals(SettingsUiState(), viewModel().state)
        }

    @Test
    fun `log out asks for confirmation first`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, SettingsEvent.LogOut)

            assertEquals(SettingsUiState(isLogOutDialogOpen = true), viewModel.state)
            assertTrue(client.sent.isEmpty())
        }

    @Test
    fun `dismissing the confirmation logs nothing out`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, SettingsEvent.LogOut, SettingsEvent.DismissLogOut)

            assertEquals(SettingsUiState(), viewModel.state)
            assertTrue(client.sent.isEmpty())
        }

    @Test
    fun `confirming logs out and shows progress until TDLib answers`() =
        runTest {
            val answer = CompletableDeferred<TdResult<TdApi.Ok>>()
            client.on(TdApi.LogOut::class) { answer.await() }
            val viewModel = viewModel()

            send(viewModel, SettingsEvent.LogOut, SettingsEvent.ConfirmLogOut)

            assertEquals(1, client.sentOf<TdApi.LogOut>().size)
            assertEquals(SettingsUiState(isLoggingOut = true), viewModel.state)
        }

    @Test
    fun `tapping log out again while logging out sends one logout`() =
        runTest {
            client.on(TdApi.LogOut::class) { CompletableDeferred<TdResult<TdApi.Ok>>().await() }
            val viewModel = viewModel()

            send(viewModel, SettingsEvent.LogOut, SettingsEvent.ConfirmLogOut, SettingsEvent.ConfirmLogOut)
            send(viewModel, SettingsEvent.LogOut)

            assertEquals(1, client.sentOf<TdApi.LogOut>().size)
            assertEquals(SettingsUiState(isLoggingOut = true), viewModel.state)
        }

    @Test
    fun `progress stays after TDLib accepted the logout, until the app leaves for the login flow`() =
        runTest {
            answerLogOut(TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, SettingsEvent.LogOut, SettingsEvent.ConfirmLogOut)

            assertTrue(viewModel.state.isLoggingOut)
            assertTrue(effects.isEmpty())
        }

    @Test
    fun `no answer in time shows a network error and allows another try`() =
        runTest {
            client.on(TdApi.LogOut::class) { CompletableDeferred<TdResult<TdApi.Ok>>().await() }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            send(viewModel, SettingsEvent.LogOut, SettingsEvent.ConfirmLogOut)

            advanceTimeBy(REQUEST_TIMEOUT)
            runCurrent()

            assertEquals(listOf(SettingsEffect.ShowError(DomainError.Network)), effects)
            assertEquals(SettingsUiState(), viewModel.state)
        }

    @Test
    fun `a flood wait shows how long to wait`() =
        runTest {
            answerLogOut(failure(code = 429, message = "Too Many Requests: retry after 42"))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, SettingsEvent.LogOut, SettingsEvent.ConfirmLogOut)

            assertEquals(listOf(SettingsEffect.ShowError(DomainError.FloodWait(42))), effects)
            assertFalse(viewModel.state.isLoggingOut)
        }

    @Test
    fun `an unauthorized session shows the error`() =
        runTest {
            answerLogOut(failure(code = 401, message = "Unauthorized"))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, SettingsEvent.LogOut, SettingsEvent.ConfirmLogOut)

            assertEquals(listOf(SettingsEffect.ShowError(DomainError.Unauthorized)), effects)
            assertFalse(viewModel.state.isLoggingOut)
        }

    @Test
    fun `a silent error shows nothing`() =
        runTest {
            answerLogOut(failure(code = 406, message = "Silent"))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, SettingsEvent.LogOut, SettingsEvent.ConfirmLogOut)

            assertTrue(effects.isEmpty())
            assertFalse(viewModel.state.isLoggingOut)
        }

    @Test
    fun `can log out again after a failure`() =
        runTest {
            answerLogOut(failure(code = 400, message = "Already logging out"))
            val viewModel = viewModel()
            send(viewModel, SettingsEvent.LogOut, SettingsEvent.ConfirmLogOut)
            answerLogOut(TdResult.Success(TdApi.Ok()))

            send(viewModel, SettingsEvent.LogOut, SettingsEvent.ConfirmLogOut)

            assertEquals(2, client.sentOf<TdApi.LogOut>().size)
            assertTrue(viewModel.state.isLoggingOut)
        }

    @Test
    fun `the open confirmation survives process death`() =
        runTest {
            send(viewModel(), SettingsEvent.LogOut)

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertEquals(SettingsUiState(isLogOutDialogOpen = true), restored.state)
        }

    @Test
    fun `a confirmed logout is not restored after process death`() =
        runTest {
            client.on(TdApi.LogOut::class) { CompletableDeferred<TdResult<TdApi.Ok>>().await() }
            send(viewModel(), SettingsEvent.LogOut, SettingsEvent.ConfirmLogOut)

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertEquals(SettingsUiState(), restored.state)
        }

    private companion object {
        /** `AUTH_REQUEST_TIMEOUT` of `core:data`: logging out is an authentication request. */
        val REQUEST_TIMEOUT = 90.seconds
    }
}
