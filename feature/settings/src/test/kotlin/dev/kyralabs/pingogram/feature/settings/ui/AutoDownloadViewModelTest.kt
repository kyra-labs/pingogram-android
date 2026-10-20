package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadSettings
import dev.kyralabs.pingogram.core.domain.model.NetworkStatus
import dev.kyralabs.pingogram.core.domain.model.NetworkType
import dev.kyralabs.pingogram.core.testing.FakeAutoDownloadRepository
import dev.kyralabs.pingogram.core.testing.FakeNetworkRepository
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoDownloadViewModelTest {
    private val repository = FakeAutoDownloadRepository()
    private val network = FakeNetworkRepository()
    private val mobile = AutoDownloadSettings.DefaultMobileRules

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** The view model of [page], its state collected as by the screen. */
    private fun TestScope.viewModel(page: AutoDownloadNetwork = AutoDownloadNetwork.Mobile): AutoDownloadViewModel {
        val viewModel = AutoDownloadViewModel(page, repository, network)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: AutoDownloadViewModel): List<AutoDownloadEffect> {
        val effects = mutableListOf<AutoDownloadEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    @Test
    fun `shows nothing before the settings are read`() =
        runTest {
            assertNull(AutoDownloadViewModel(AutoDownloadNetwork.WiFi, repository, network).uiState.value.rules)
        }

    @Test
    fun `shows the page's network's rules`() =
        runTest {
            val state = viewModel(AutoDownloadNetwork.WiFi).uiState.value

            assertEquals(AutoDownloadNetwork.WiFi, state.network)
            assertEquals(AutoDownloadSettings.DefaultWiFiRules, state.rules)
        }

    @Test
    fun `a change shows at once and is saved for the page's network only`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(AutoDownloadEvent.Change(mobile.copy(videos = true)))
            runCurrent()

            assertEquals(mobile.copy(videos = true), viewModel.uiState.value.rules)
            assertEquals(AutoDownloadSettings(mobile = mobile.copy(videos = true)), repository.settings.value)
        }

    @Test
    fun `the steps of a dragged size limit are saved in order, ending at the last one`() =
        runTest {
            val viewModel = viewModel()

            listOf(5_000_000L, 10_000_000L, 15_000_000L).forEach {
                viewModel.onEvent(AutoDownloadEvent.Change(mobile.copy(videoSizeLimit = it)))
            }
            runCurrent()

            assertEquals(15_000_000L, repository.settings.value.mobile.videoSizeLimit)
            assertEquals(
                15_000_000L,
                viewModel.uiState.value.rules
                    ?.videoSizeLimit,
            )
        }

    @Test
    fun `a change that cannot be saved shows what was saved and says why`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            repository.failure = DomainError.Unknown(0, "Disk full")

            viewModel.onEvent(AutoDownloadEvent.Change(mobile.copy(files = true)))
            runCurrent()

            assertEquals(mobile, viewModel.uiState.value.rules)
            assertEquals(listOf(AutoDownloadEffect.ShowError(DomainError.Unknown(0, "Disk full"))), effects)
        }

    @Test
    fun `Data Saver's note shows on mobile data and roaming, never on Wi-Fi`() =
        runTest {
            network.networkStatus.value = NetworkStatus(NetworkType.Mobile, isMetered = true, isDataSaverOn = true)

            assertTrue(viewModel(AutoDownloadNetwork.Mobile).uiState.value.isDataSaverOn)
            assertTrue(viewModel(AutoDownloadNetwork.Roaming).uiState.value.isDataSaverOn)
            assertFalse(viewModel(AutoDownloadNetwork.WiFi).uiState.value.isDataSaverOn)
        }

    @Test
    fun `Data Saver is followed while the page shows`() =
        runTest {
            val viewModel = viewModel()

            network.networkStatus.value = NetworkStatus(NetworkType.WiFi, isMetered = false, isDataSaverOn = true)
            runCurrent()

            assertTrue(viewModel.uiState.value.isDataSaverOn)
        }
}
