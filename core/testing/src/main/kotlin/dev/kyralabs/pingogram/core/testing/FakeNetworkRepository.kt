package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.domain.model.NetworkStatus
import dev.kyralabs.pingogram.core.domain.model.NetworkType
import dev.kyralabs.pingogram.core.domain.repository.NetworkRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [NetworkRepository] that reports [networkStatus], Wi-Fi without Data Saver unless [initial]; set it to change. */
class FakeNetworkRepository(
    initial: NetworkStatus = NetworkStatus(NetworkType.WiFi, isMetered = false, isDataSaverOn = false),
) : NetworkRepository {
    override val networkStatus = MutableStateFlow(initial)
}
