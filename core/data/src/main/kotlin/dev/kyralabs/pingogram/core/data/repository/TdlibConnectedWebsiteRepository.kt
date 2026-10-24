package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.domain.model.ConnectedWebsite
import dev.kyralabs.pingogram.core.domain.repository.ConnectedWebsiteRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ConnectedWebsiteRepository] on TDLib's `getConnectedWebsites`, `disconnectWebsite` and `disconnectAllWebsites`,
 * each a request to Telegram.
 */
@Singleton
class TdlibConnectedWebsiteRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : ConnectedWebsiteRepository {
        override suspend fun websites(): Outcome<ImmutableList<ConnectedWebsite>> =
            client.request(TdApi.GetConnectedWebsites()).map { websites ->
                websites.websites
                    .orEmpty()
                    .map { it.toWebsite() }
                    .toImmutableList()
            }

        override suspend fun disconnect(websiteId: Long): Outcome<Unit> =
            client.request(TdApi.DisconnectWebsite(websiteId)).map {}

        override suspend fun disconnectAll(): Outcome<Unit> = client.request(TdApi.DisconnectAllWebsites()).map {}
    }

private fun TdApi.ConnectedWebsite.toWebsite() =
    ConnectedWebsite(
        id = id,
        domain = domainName.orEmpty(),
        botUserId = botUserId,
        browser = browser.orEmpty(),
        platform = platform.orEmpty(),
        loggedInAt = logInDate.toLong(),
        lastActiveAt = lastActiveDate.toLong(),
        ipAddress = ipAddress.orEmpty(),
        location = location.orEmpty(),
    )
