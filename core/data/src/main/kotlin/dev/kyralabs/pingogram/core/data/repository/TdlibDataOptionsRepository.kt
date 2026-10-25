package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.domain.repository.DataOptionsRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.flow.Flow
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DataOptionsRepository] on TDLib: its option `prefer_ipv6`, read with `getOption` once per collection and followed
 * through `updateOption`, to which it subscribes first; and `clearAllDraftMessages` without secret chats. No cache, as
 * TDLib keeps both.
 */
@Singleton
class TdlibDataOptionsRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : DataOptionsRepository {
        override val prefersIpv6: Flow<Boolean> = client.booleanOption(PREFER_IPV6)

        override suspend fun setPrefersIpv6(prefers: Boolean): Outcome<Unit> =
            client.setBooleanOption(PREFER_IPV6, prefers)

        override suspend fun deleteCloudDrafts(): Outcome<Unit> =
            client.request(TdApi.ClearAllDraftMessages(true)).map {}

        private companion object {
            const val PREFER_IPV6 = "prefer_ipv6"
        }
    }
