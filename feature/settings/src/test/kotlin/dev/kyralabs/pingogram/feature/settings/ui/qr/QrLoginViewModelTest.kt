package dev.kyralabs.pingogram.feature.settings.ui.qr

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibSessionRepository
import dev.kyralabs.pingogram.core.domain.model.SessionDevice
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.feature.settings.ui.testTimeFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
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

/** Logging in another device by its QR code: nothing is sent until "Log in". */
@OptIn(ExperimentalCoroutinesApi::class)
class QrLoginViewModelTest {
    private val client = FakeTdlibClient()
    private val created = mutableListOf<QrLoginViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    // Through the real TDLib repository on FakeTdlibClient, as the app wires it.
    private fun viewModel() =
        QrLoginViewModel(LINK, TdlibSessionRepository(client), testTimeFormats()).also { created += it }

    private fun session() =
        TdApi.Session(
            7,
            false,
            false,
            false,
            true,
            true,
            TdApi.SessionDeviceTypeMac(),
            2040,
            "Telegram Desktop",
            "6.2",
            true,
            "MacBook Pro",
            "macOS",
            "16",
            1_760_780_000,
            1_760_780_400,
            "203.0.113.7",
            "Berlin, Germany",
        )

    @Test
    fun `asks first and sends nothing`() =
        runTest {
            viewModel()
            runCurrent()

            assertTrue(client.sent.isEmpty())
        }

    @Test
    fun `logs the device in on Log in, and shows its session`() =
        runTest {
            client.on(TdApi.ConfirmQrCodeAuthentication::class, TdResult.Success(session()))
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(QrLoginEvent.Confirm)
                assertEquals(QrLoginUiState.Confirm(isConfirming = true), viewModel.uiState.value)
                runCurrent()
                assertEquals(QrLoginEffect.Confirmed, awaitItem())
            }
            val done = viewModel.uiState.value as QrLoginUiState.Done
            assertEquals(7L, done.session.id)
            assertEquals(SessionDevice.Desktop, done.session.device)
            assertEquals(LINK, client.sentOf<TdApi.ConfirmQrCodeAuthentication>().single().link)
        }

    @Test
    fun `taps while it confirms send it once`() =
        runTest {
            client.on(TdApi.ConfirmQrCodeAuthentication::class, TdResult.Success(session()))
            val viewModel = viewModel()

            viewModel.onEvent(QrLoginEvent.Confirm)
            viewModel.onEvent(QrLoginEvent.Confirm)
            runCurrent()

            assertEquals(1, client.sentOf<TdApi.ConfirmQrCodeAuthentication>().size)
        }

    @Test
    fun `an expired code says so, with a Reject haptic`() =
        runTest {
            client.on(TdApi.ConfirmQrCodeAuthentication::class, TdResult.Failure(TdError(400, "AUTH_TOKEN_EXPIRED")))
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(QrLoginEvent.Confirm)
                runCurrent()
                assertEquals(QrLoginEffect.Rejected, awaitItem())
            }
            assertEquals(QrLoginUiState.Confirm(error = DomainError.QrLoginExpired), viewModel.uiState.value)
        }

    @Test
    fun `without network it can be tried again`() =
        runTest {
            val offline = TdError(500, "Network is unreachable")
            client.on(TdApi.ConfirmQrCodeAuthentication::class, TdResult.Failure(offline))
            val viewModel = viewModel()
            viewModel.onEvent(QrLoginEvent.Confirm)
            runCurrent()
            assertEquals(QrLoginUiState.Confirm(error = DomainError.Network), viewModel.uiState.value)

            client.on(TdApi.ConfirmQrCodeAuthentication::class, TdResult.Success(session()))
            viewModel.onEvent(QrLoginEvent.Confirm)
            runCurrent()

            assertTrue(viewModel.uiState.value is QrLoginUiState.Done)
        }

    private companion object {
        const val LINK = "tg://login?token=abc"
    }
}
