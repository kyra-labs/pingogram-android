package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toNetworkUsage
import dev.kyralabs.pingogram.core.domain.model.NetworkUsage
import dev.kyralabs.pingogram.core.domain.repository.NetworkUsageRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [NetworkUsageRepository] on TDLib's `getNetworkStatistics` (every session since the last reset, not only this one)
 * and `resetNetworkStatistics`, both answered from TDLib's database.
 */
@AccountScoped
class TdlibNetworkUsageRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : NetworkUsageRepository {
        override suspend fun usage(): Outcome<NetworkUsage> =
            client.request(TdApi.GetNetworkStatistics(false)).map { it.toNetworkUsage() }

        override suspend fun reset(): Outcome<Unit> = client.request(TdApi.ResetNetworkStatistics()).map {}
    }
