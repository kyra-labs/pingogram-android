package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadRules
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadSettings
import dev.kyralabs.pingogram.core.domain.model.NetworkStatus
import dev.kyralabs.pingogram.core.domain.model.NetworkType
import dev.kyralabs.pingogram.core.testing.FakeAutoDownloadRepository
import dev.kyralabs.pingogram.core.testing.FakeNetworkRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultAutoDownloadPolicyTest {
    private val settings = FakeAutoDownloadRepository()
    private val network = FakeNetworkRepository()
    private val policy = DefaultAutoDownloadPolicy(settings, network)

    @Test
    fun `follows the network the device uses`() =
        runTest {
            policy.rules.test {
                assertEquals(AutoDownloadSettings.DefaultWiFiRules, awaitItem())

                network.networkStatus.value = status(NetworkType.Mobile)
                assertEquals(AutoDownloadSettings.DefaultMobileRules, awaitItem())

                network.networkStatus.value = status(NetworkType.Roaming)
                assertEquals(AutoDownloadRules.Nothing, awaitItem())
            }
        }

    @Test
    fun `follows the settings`() =
        runTest {
            policy.rules.test {
                awaitItem()

                settings.setRules(AutoDownloadNetwork.WiFi, AutoDownloadRules(files = true))

                assertEquals(AutoDownloadRules(files = true), awaitItem())
            }
        }

    @Test
    fun `nothing downloads by itself while Data Saver holds back mobile data`() =
        runTest {
            network.networkStatus.value = status(NetworkType.Mobile, dataSaver = true)

            policy.rules.test { assertEquals(AutoDownloadRules.Nothing, awaitItem()) }
        }

    @Test
    fun `offline keeps the last network's rules`() =
        runTest {
            network.networkStatus.value = status(NetworkType.Mobile)

            policy.rules.test {
                assertEquals(AutoDownloadSettings.DefaultMobileRules, awaitItem())

                network.networkStatus.value = NetworkStatus.Offline
                network.networkStatus.value = status(NetworkType.Mobile)
                expectNoEvents()
            }
        }

    @Test
    fun `offline since the start downloads nothing by itself`() =
        runTest {
            network.networkStatus.value = NetworkStatus.Offline

            policy.rules.test { assertEquals(AutoDownloadRules.Nothing, awaitItem()) }
        }

    private fun status(
        type: NetworkType,
        dataSaver: Boolean = false,
    ) = NetworkStatus(type, isMetered = type != NetworkType.WiFi, isDataSaverOn = dataSaver)
}
