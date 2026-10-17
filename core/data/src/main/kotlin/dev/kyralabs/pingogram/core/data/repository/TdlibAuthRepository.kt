package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toAuthStep
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onSubscription
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AuthRepository] on TDLib's authentication API (`docs/TDLIB.md`). It never logs: phone numbers, codes and passwords
 * pass through only to TDLib. One function per request, as in [AuthRepository] (hence `TooManyFunctions`).
 */
@Suppress("TooManyFunctions")
@Singleton
class TdlibAuthRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : AuthRepository {
        override val authStep: Flow<AuthStep> =
            client.updates
                // Read the current state only once subscribed, so no update in between is missed. The update that set
                // it may still arrive and repeat it; steps are idempotent for collectors.
                .onSubscription { emit(CurrentAuthState) }
                .mapNotNull { update ->
                    when {
                        update === CurrentAuthState -> client.authState.value

                        // From the updates rather than authState, which is conflated and can skip Closed.
                        update is TdApi.UpdateAuthorizationState -> AuthState.from(update.authorizationState)

                        else -> null
                    }
                }.map(AuthState::toAuthStep)
                .flowOn(dispatcher)

        override suspend fun setPhoneNumber(phoneNumber: String): Outcome<Unit> =
            client.request(TdApi.SetAuthenticationPhoneNumber(phoneNumber, phoneNumberAuthenticationSettings())).map {}

        override suspend fun resendCode(): Outcome<Unit> =
            client.request(TdApi.ResendAuthenticationCode(TdApi.ResendCodeReasonUserRequest())).map {}

        override suspend fun checkCode(code: String): Outcome<Unit> =
            client.request(TdApi.CheckAuthenticationCode(code)).map {}

        override suspend fun requestQrCode(): Outcome<Unit> =
            // No other accounts: Pingogram has one account per installation for now.
            client.request(TdApi.RequestQrCodeAuthentication(LongArray(0))).map {}

        // TDLib accepts neither setAuthenticationPhoneNumber nor requestQrCodeAuthentication while it waits for the QR
        // code to be scanned (td/telegram/AuthManager.cpp). logOut on an unauthorized session destroys its keys and
        // closes TDLib; TdlibClient then starts a new instance, which asks for a phone number again.
        override suspend fun cancelQrCode(): Outcome<Unit> = client.request(TdApi.LogOut()).map {}

        override suspend fun checkPassword(password: String): Outcome<Unit> =
            client.request(TdApi.CheckAuthenticationPassword(password)).map {}

        override suspend fun requestPasswordRecovery(): Outcome<Unit> =
            client.request(TdApi.RequestAuthenticationPasswordRecovery()).map {}

        override suspend fun checkPasswordRecoveryCode(code: String): Outcome<Unit> =
            client.request(TdApi.CheckAuthenticationPasswordRecoveryCode(code)).map {}

        override suspend fun recoverPassword(
            code: String,
            newPassword: String,
            newHint: String,
        ): Outcome<Unit> = client.request(TdApi.RecoverAuthenticationPassword(code, newPassword, newHint)).map {}

        override suspend fun register(
            firstName: String,
            lastName: String,
        ): Outcome<Unit> = client.request(TdApi.RegisterUser(firstName, lastName, false)).map {}

        override suspend fun setEmailAddress(emailAddress: String): Outcome<Unit> =
            client.request(TdApi.SetAuthenticationEmailAddress(emailAddress)).map {}

        override suspend fun checkEmailCode(code: String): Outcome<Unit> =
            client.request(TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(code))).map {}

        override suspend fun resetEmailAddress(): Outcome<Unit> =
            client.request(TdApi.ResetAuthenticationEmailAddress()).map {}

        override suspend fun logOut(): Outcome<Unit> = client.request(TdApi.LogOut()).map {}
    }

/**
 * Login code settings within the Telegram API terms (CLAUDE.md rule 3) and the permissions Pingogram has: no flash or
 * missed call verification (both read the call log), no SMS Retriever API and no Firebase (official apps only), and
 * no claim that the number belongs to this device. No logout tokens are kept yet.
 */
internal fun phoneNumberAuthenticationSettings() =
    TdApi.PhoneNumberAuthenticationSettings().apply {
        allowFlashCall = false
        allowMissedCall = false
        isCurrentPhoneNumber = false
        hasUnknownPhoneNumber = false
        allowSmsRetrieverApi = false
        firebaseAuthenticationSettings = null
        authenticationTokens = emptyArray()
    }

/** Marks the point of subscription in [TdlibAuthRepository.authStep]; never sent by TDLib. */
private object CurrentAuthState : TdApi.Update() {
    override fun getConstructor() = 0
}
