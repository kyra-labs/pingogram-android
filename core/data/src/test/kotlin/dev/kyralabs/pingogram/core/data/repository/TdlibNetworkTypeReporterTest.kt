package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.NetworkStatus
import dev.kyralabs.pingogram.core.domain.model.NetworkType
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TdlibNetworkTypeReporterTest {
    private val client =
        FakeTdlibClient(authState = AuthState.WaitTdlibParameters).apply {
            on(TdApi.SetNetworkType::class, TdResult.Success(TdApi.Ok()))
            on(TdApi.SetOption::class, TdResult.Success(TdApi.Ok()))
        }
    private val network = MutableStateFlow(network(handle = 1, NetworkType.WiFi))
    private val foreground = MutableStateFlow(false)

    private fun TestScope.started() {
        TdlibNetworkTypeReporter(client, network, foreground, backgroundScope).start()
        runCurrent()
    }

    private fun sentOnline() =
        client.sentOf<TdApi.SetOption>().map {
            assertEquals("online", it.name)
            (it.value as TdApi.OptionValueBoolean).value
        }

    private fun sentTypes() = client.sentOf<TdApi.SetNetworkType>().map { it.type.constructor }

    @Test
    fun `waits for TDLib's parameters, then sends the network`() =
        runTest {
            started()
            assertEquals(emptyList<Int>(), sentTypes())

            client.authState.value = AuthState.WaitPhoneNumber
            runCurrent()

            assertEquals(listOf(TdApi.NetworkTypeWiFi.CONSTRUCTOR), sentTypes())
        }

    @Test
    fun `sends every new network, of the same kind too, and each kind as TDLib names it`() =
        runTest {
            client.authState.value = AuthState.Ready
            started()

            network.value = network(handle = 2, NetworkType.WiFi)
            runCurrent()
            network.value = network(handle = 3, NetworkType.Mobile)
            runCurrent()
            network.value = network(handle = 4, NetworkType.Roaming)
            runCurrent()
            network.value = network(handle = null, NetworkType.None)
            runCurrent()

            assertEquals(
                listOf(
                    TdApi.NetworkTypeWiFi.CONSTRUCTOR,
                    TdApi.NetworkTypeWiFi.CONSTRUCTOR,
                    TdApi.NetworkTypeMobile.CONSTRUCTOR,
                    TdApi.NetworkTypeMobileRoaming.CONSTRUCTOR,
                    TdApi.NetworkTypeNone.CONSTRUCTOR,
                ),
                sentTypes(),
            )
        }

    @Test
    fun `sends nothing again when only Data Saver or the metered state changes`() =
        runTest {
            client.authState.value = AuthState.Ready
            started()

            network.value =
                NetworkStatus(NetworkType.WiFi, isMetered = true, isDataSaverOn = true).let { DefaultNetwork(1, it) }
            runCurrent()

            assertEquals(1, sentTypes().size)
        }

    @Test
    fun `a new TDLib instance after a logout hears the network again`() =
        runTest {
            client.authState.value = AuthState.Ready
            started()

            client.authState.value = AuthState.Closed
            runCurrent()
            client.authState.value = AuthState.WaitTdlibParameters
            runCurrent()
            client.authState.value = AuthState.WaitPhoneNumber
            runCurrent()

            assertEquals(listOf(TdApi.NetworkTypeWiFi.CONSTRUCTOR, TdApi.NetworkTypeWiFi.CONSTRUCTOR), sentTypes())
        }

    @Test
    fun `tells TDLib whether the app is in use, once it has its parameters`() =
        runTest {
            foreground.value = true
            started()
            assertEquals(emptyList<Boolean>(), sentOnline())

            client.authState.value = AuthState.Ready
            runCurrent()
            foreground.value = false
            runCurrent()
            foreground.value = true
            runCurrent()

            assertEquals(listOf(true, false, true), sentOnline())
        }

    @Test
    fun `coming back while TDLib is not connected sends the network again, so it reconnects at once`() =
        runTest {
            client.authState.value = AuthState.Ready
            client.connectionState.value = ConnectionState.Connecting
            started()

            foreground.value = true
            runCurrent()

            assertEquals(listOf(TdApi.NetworkTypeWiFi.CONSTRUCTOR, TdApi.NetworkTypeWiFi.CONSTRUCTOR), sentTypes())
        }

    @Test
    fun `coming back while connected sends no network`() =
        runTest {
            client.authState.value = AuthState.Ready
            client.connectionState.value = ConnectionState.Ready
            started()

            foreground.value = true
            runCurrent()

            assertEquals(listOf(TdApi.NetworkTypeWiFi.CONSTRUCTOR), sentTypes())
            assertEquals(listOf(false, true), sentOnline())
        }

    private fun network(
        handle: Long?,
        type: NetworkType,
    ) = DefaultNetwork(handle, NetworkStatus(type, isMetered = type != NetworkType.WiFi, isDataSaverOn = false))
}
