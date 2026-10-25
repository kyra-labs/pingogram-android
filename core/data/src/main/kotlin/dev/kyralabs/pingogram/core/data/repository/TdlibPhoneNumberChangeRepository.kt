package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toAuthCodeType
import dev.kyralabs.pingogram.core.domain.model.LoginCodeInfo
import dev.kyralabs.pingogram.core.domain.repository.PhoneNumberChangeRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [PhoneNumberChangeRepository] on TDLib's `sendPhoneNumberCode` with `phoneNumberCodeTypeChange`,
 * `resendPhoneNumberCode` and `checkPhoneNumberCode`, with the login's code settings (no flash or missed calls, no SMS
 * Retriever, no Firebase: [phoneNumberAuthenticationSettings]). They wait as long as authentication requests
 * ([AUTH_REQUEST_TIMEOUT]). No cache: TDLib keeps the pending change, and announces the new number with `updateUser`.
 */
@AccountScoped
class TdlibPhoneNumberChangeRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : PhoneNumberChangeRepository {
        override suspend fun sendCode(phoneNumber: String): Outcome<LoginCodeInfo> =
            client
                .request(
                    TdApi.SendPhoneNumberCode(
                        phoneNumber,
                        phoneNumberAuthenticationSettings(),
                        TdApi.PhoneNumberCodeTypeChange(),
                    ),
                    AUTH_REQUEST_TIMEOUT,
                ).map { it.toLoginCodeInfo() }

        override suspend fun resendCode(): Outcome<LoginCodeInfo> =
            client
                .request(TdApi.ResendPhoneNumberCode(TdApi.ResendCodeReasonUserRequest()), AUTH_REQUEST_TIMEOUT)
                .map { it.toLoginCodeInfo() }

        override suspend fun checkCode(code: String): Outcome<Unit> =
            client.request(TdApi.CheckPhoneNumberCode(code), AUTH_REQUEST_TIMEOUT).map {}
    }

private fun TdApi.AuthenticationCodeInfo.toLoginCodeInfo() =
    LoginCodeInfo(
        phoneNumber = phoneNumber.orEmpty(),
        type = type.toAuthCodeType(),
        nextType = nextType?.toAuthCodeType(),
        timeoutSeconds = timeout.coerceAtLeast(0),
    )
