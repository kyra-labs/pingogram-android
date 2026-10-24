package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibNetworkUsageRepository
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.DataAmount
import dev.kyralabs.pingogram.core.domain.model.DataKind
import dev.kyralabs.pingogram.core.domain.model.NetworkStatus
import dev.kyralabs.pingogram.core.domain.model.NetworkType
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeNetworkRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkUsageViewModelTest {
    private val client = FakeTdlibClient()
    private val network = FakeNetworkRepository()
    private val savedState = SavedStateHandle()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        answer(received = 100)
        client.on(TdApi.ResetNetworkStatistics::class) {
            answer(received = 0)
            TdResult.Success(TdApi.Ok())
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun answer(received: Long) =
        client.on(
            TdApi.GetNetworkStatistics::class,
            TdResult.Success(
                TdApi.NetworkStatistics(
                    1_700_000_000,
                    arrayOf(
                        TdApi.NetworkStatisticsEntryFile(TdApi.FileTypePhoto(), TdApi.NetworkTypeWiFi(), 0, received),
                    ),
                ),
            ),
        )

    private fun TestScope.viewModel() =
        NetworkUsageViewModel(savedState, TdlibNetworkUsageRepository(client), network).also { runCurrent() }

    private val NetworkUsageViewModel.content get() = uiState.value as NetworkUsageUiState.Content

    @Test
    fun `shows the usage of the network the device uses now`() =
        runTest {
            network.networkStatus.value = NetworkStatus(NetworkType.Mobile, isMetered = true, isDataSaverOn = false)

            val content = viewModel().content

            assertEquals(AutoDownloadNetwork.Mobile, content.network)
            assertEquals(DataAmount(0, 100), content.usage.usage[AutoDownloadNetwork.WiFi]?.get(DataKind.Photos))
        }

    @Test
    fun `the chosen network survives process death`() =
        runTest {
            viewModel().onEvent(NetworkUsageEvent.SelectNetwork(AutoDownloadNetwork.Roaming))

            assertEquals(AutoDownloadNetwork.Roaming, viewModel().content.network)
        }

    @Test
    fun `resetting asks first, then counts anew`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(NetworkUsageEvent.Reset)
            assertTrue(viewModel.content.isResetDialogOpen)
            assertTrue(client.sentOf<TdApi.ResetNetworkStatistics>().isEmpty())

            viewModel.onEvent(NetworkUsageEvent.ConfirmReset)
            runCurrent()

            assertEquals(NetworkUsageEffect.WasReset, viewModel.effects.first())
            assertFalse(viewModel.content.isResetting)
            assertEquals(null, viewModel.content.usage.usage[AutoDownloadNetwork.WiFi])
        }

    @Test
    fun `a failed reset keeps the counts and says why`() =
        runTest {
            client.on(TdApi.ResetNetworkStatistics::class, TdResult.Failure(TdError(500, "Internal")))
            val viewModel = viewModel()

            viewModel.onEvent(NetworkUsageEvent.Reset)
            viewModel.onEvent(NetworkUsageEvent.ConfirmReset)
            runCurrent()

            assertEquals(NetworkUsageEffect.ShowError(DomainError.Unknown(500, "Internal")), viewModel.effects.first())
            assertEquals(
                DataAmount(0, 100),
                viewModel.content.usage.usage[AutoDownloadNetwork.WiFi]
                    ?.get(DataKind.Photos),
            )
        }

    @Test
    fun `statistics that cannot be read show the error, and try again`() =
        runTest {
            client.on(TdApi.GetNetworkStatistics::class, TdResult.Failure(TdError(500, "Internal")))
            val viewModel = viewModel()
            assertTrue(viewModel.uiState.value is NetworkUsageUiState.Error)

            answer(received = 5)
            viewModel.onEvent(NetworkUsageEvent.Retry)
            runCurrent()

            assertEquals(
                DataAmount(0, 5),
                viewModel.content.usage.usage[AutoDownloadNetwork.WiFi]
                    ?.get(DataKind.Photos),
            )
        }
}
