package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.domain.repository.AccountRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AccountRepository] on TDLib's `getAccountTtl`, `setAccountTtl` and `deleteAccount`, which ask Telegram each time (no
 * cache, no updates). Deleting checks the 2-step verification password, so it waits as long as authentication
 * requests ([AUTH_REQUEST_TIMEOUT]). Once Telegram deleted the account TDLib closes the session by itself
 * (`updateAuthorizationState` with `LoggingOut`, then `Closed`), which the app handles as a logout.
 */
@Singleton
class TdlibAccountRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : AccountRepository {
        override suspend fun accountTtl(): Outcome<Int> = client.request(TdApi.GetAccountTtl()).map { it.days }

        override suspend fun setAccountTtl(days: Int): Outcome<Unit> =
            client.request(TdApi.SetAccountTtl(TdApi.AccountTtl(days))).map {}

        override suspend fun deleteAccount(
            reason: String,
            password: String,
        ): Outcome<Unit> = client.request(TdApi.DeleteAccount(reason, password), AUTH_REQUEST_TIMEOUT).map {}
    }
