package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.AutoDownloadRules
import dev.kyralabs.pingogram.core.domain.model.NetworkStatus
import dev.kyralabs.pingogram.core.domain.model.NetworkType
import dev.kyralabs.pingogram.core.domain.repository.AutoDownloadPolicy
import dev.kyralabs.pingogram.core.domain.repository.AutoDownloadRepository
import dev.kyralabs.pingogram.core.domain.repository.NetworkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.runningFold
import javax.inject.Inject

/**
 * [AutoDownloadPolicy] from the settings and the network (`AutoDownloadSettings.rulesOn`). While offline the rules of
 * the last network stay, so bubbles do not switch to download buttons and back at every short loss of the network
 * (TDLib waits with downloads until it is back anyway); without any network since the start, nothing downloads by
 * itself.
 */
class DefaultAutoDownloadPolicy
    @Inject
    constructor(
        private val settingsRepository: AutoDownloadRepository,
        private val networkRepository: NetworkRepository,
    ) : AutoDownloadPolicy {
        override val rules: Flow<AutoDownloadRules> =
            combine(
                settingsRepository.settings,
                networkRepository.networkStatus.keepingLastNetwork(),
            ) { settings, status ->
                settings.rulesOn(status)
            }.distinctUntilChanged()
    }

/** The status of the last network while there is none; offline only until the first network. */
private fun Flow<NetworkStatus>.keepingLastNetwork(): Flow<NetworkStatus> =
    runningFold(NetworkStatus.Offline) { last, status ->
        if (status.type == NetworkType.None && last.type != NetworkType.None) last else status
    }.drop(1)
