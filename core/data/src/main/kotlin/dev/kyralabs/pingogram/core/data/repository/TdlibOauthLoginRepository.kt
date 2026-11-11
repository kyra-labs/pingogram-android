package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.OauthLoginRequest
import dev.kyralabs.pingogram.core.domain.repository.OauthLoginRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [OauthLoginRepository] on TDLib, no cache: `getOauthLinkInfo`, `checkOauthRequestMatchCode`, `acceptOauthRequest`
 * (never with TON Connect's data: Pingogram shares no wallet) and `declineOauthRequest`; the requests Telegram sends
 * are `updateNewOauthRequest`'s links.
 */
@AccountScoped
class TdlibOauthLoginRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : OauthLoginRepository {
        override val requests: Flow<String> =
            client.updates.filterIsInstance<TdApi.UpdateNewOauthRequest>().map { it.url }

        override suspend fun request(url: String): Outcome<OauthLoginRequest> =
            client.request(TdApi.GetOauthLinkInfo(url, "")).map { it.toRequest() }

        override suspend fun checkMatchCode(
            url: String,
            code: String,
        ): Outcome<Unit> = client.request(TdApi.CheckOauthRequestMatchCode(url, code)).map {}

        override suspend fun accept(
            url: String,
            matchCode: String,
            allowMessages: Boolean,
            sharePhoneNumber: Boolean,
        ): Outcome<String> =
            client
                .request(TdApi.AcceptOauthRequest(url, matchCode, allowMessages, sharePhoneNumber, null))
                .map { it.url }

        override suspend fun decline(url: String): Outcome<Unit> = client.request(TdApi.DeclineOauthRequest(url)).map {}
    }

private fun TdApi.OauthLinkInfo.toRequest() =
    OauthLoginRequest(
        domain = domain,
        appName = verifiedAppName,
        isApp = fromApp,
        botUserId = botUserId,
        asksToMessage = requestWriteAccess,
        asksForPhoneNumber = requestPhoneNumberAccess,
        asksForTonWallet = requestTonWalletAccess,
        browser = browser,
        platform = platform,
        location = location,
        // TDLib asks for a code first only where it says so; its codes are irrelevant otherwise.
        matchCodes = if (matchCodeFirst) matchCodes.orEmpty().toList() else emptyList(),
    )
