package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.model.NetworkStatus
import dev.kyralabs.pingogram.core.domain.repository.CallDataSaving
import dev.kyralabs.pingogram.core.domain.repository.CallSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.NetworkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** The network as the call managers see it ([DataSavingNetworkRepository]). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class CallNetwork

/**
 * The device's network for calls, with "Use less data for calls" (Settings → Data and storage, [CallDataSaving]): where
 * the setting applies, the network reads as one that Data Saver holds back, so the video a call sends is at most
 * 360p ([dev.kyralabs.pingogram.feature.calls.engine.VideoQuality.forNetwork]), as with Data Saver itself. Nothing
 * else in a call follows the network.
 */
@Singleton
internal class DataSavingNetworkRepository
    @Inject
    constructor(
        network: NetworkRepository,
        settings: CallSettingsRepository,
        @ApplicationScope scope: CoroutineScope,
    ) : NetworkRepository {
        override val networkStatus: StateFlow<NetworkStatus> =
            combine(network.networkStatus, settings.dataSaving, ::withDataSaving)
                .stateIn(scope, SharingStarted.WhileSubscribed(), network.networkStatus.value)
    }

/** [network] as calls see it with [saving]: held back by Data Saver where the setting applies. */
internal fun withDataSaving(
    network: NetworkStatus,
    saving: CallDataSaving,
): NetworkStatus =
    if (saving.appliesTo(network) && !network.savesData) {
        network.copy(isMetered = true, isDataSaverOn = true)
    } else {
        network
    }
