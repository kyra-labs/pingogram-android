package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.EmailCodeInfo
import dev.kyralabs.pingogram.core.domain.repository.LoginEmailRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [LoginEmailRepository] on TDLib's `getPasswordState` (its `login_email_address_pattern`), `setLoginEmailAddress`,
 * `resendLoginEmailAddressCode` and `checkLoginEmailAddressCode`, which ask Telegram each time (no cache, no updates).
 * They wait as long as authentication requests ([AUTH_REQUEST_TIMEOUT]), as the login code ones do.
 */
@AccountScoped
class TdlibLoginEmailRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : LoginEmailRepository {
        override suspend fun address(): Outcome<String?> =
            client.request(TdApi.GetPasswordState()).map { state -> state.loginEmailAddressPattern?.ifBlank { null } }

        override suspend fun setAddress(address: String): Outcome<EmailCodeInfo> =
            client.request(TdApi.SetLoginEmailAddress(address), AUTH_REQUEST_TIMEOUT).map { it.toEmailCodeInfo() }

        override suspend fun resendCode(): Outcome<EmailCodeInfo> =
            client.request(TdApi.ResendLoginEmailAddressCode(), AUTH_REQUEST_TIMEOUT).map { it.toEmailCodeInfo() }

        override suspend fun checkCode(code: String): Outcome<Unit> =
            client
                .request(
                    TdApi.CheckLoginEmailAddressCode(TdApi.EmailAddressAuthenticationCode(code)),
                    AUTH_REQUEST_TIMEOUT,
                ).map {}
    }

private fun TdApi.EmailAddressAuthenticationCodeInfo.toEmailCodeInfo() =
    EmailCodeInfo(addressPattern = emailAddressPattern.orEmpty(), length = length.coerceAtLeast(0))
