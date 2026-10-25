package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toAuthStep
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.model.TelegramServers
import dev.kyralabs.pingogram.core.domain.model.telegramServersFor
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.TdlibServers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

/**
 * [AuthRepository] on TDLib's authentication API (`docs/TDLIB.md`). It never logs: phone numbers, codes and passwords
 * pass through only to TDLib. One function per request, as in [AuthRepository] (hence `TooManyFunctions`).
 *
 * Requests wait up to [AUTH_REQUEST_TIMEOUT], longer than TDLib's own limit. TDLib's timeouts in steps become
 * deadlines on [timeSource] from when this singleton first saw the state, so they survive recreated screens.
 */
@Suppress("TooManyFunctions")
@Singleton
class TdlibAuthRepository(
    private val client: TdlibClient,
    dispatcher: CoroutineDispatcher,
    timeSource: TimeSource.WithComparableMarks,
) : AuthRepository {
    @Inject
    constructor(
        client: TdlibClient,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(client, dispatcher, ElapsedRealtimeTimeSource)

    private val firstSeen = FirstSeen(timeSource)

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
            }.map { state -> state.toAuthStep(receivedAt = firstSeen.markOf(state.timeoutPayload())) }
            .flowOn(dispatcher)

    override val servers: Flow<TelegramServers> = client.servers.map { it.toTelegramServers() }

    override suspend fun setPhoneNumber(phoneNumber: String): Outcome<Unit> {
        val servers = telegramServersFor(phoneNumber).toTdlibServers()
        if (!client.switchServers(servers)) return Outcome.Failure(DomainError.Network)
        // TDLib may have restarted meanwhile on other servers (a logout): never send a number to the wrong ones.
        if (client.servers.value != servers) return Outcome.Failure(DomainError.Network)
        return client
            .authRequest(
                TdApi.SetAuthenticationPhoneNumber(phoneNumber, phoneNumberAuthenticationSettings()),
            ).map {}
    }

    override suspend fun resendCode(): Outcome<Unit> =
        client.authRequest(TdApi.ResendAuthenticationCode(TdApi.ResendCodeReasonUserRequest())).map {}

    override suspend fun checkCode(code: String): Outcome<Unit> =
        client.authRequest(TdApi.CheckAuthenticationCode(code)).map {}

    override suspend fun requestQrCode(): Outcome<Unit> {
        // Test accounts sign in with their number only; a QR code is for the user's own account.
        if (!client.switchServers(TdlibServers.Production)) return Outcome.Failure(DomainError.Network)
        if (client.servers.value != TdlibServers.Production) return Outcome.Failure(DomainError.Network)
        // No other accounts: Pingogram has one account per installation for now.
        return client.authRequest(TdApi.RequestQrCodeAuthentication(LongArray(0))).map {}
    }

    // TDLib accepts neither setAuthenticationPhoneNumber nor requestQrCodeAuthentication while it waits for the QR
    // code to be scanned (td/telegram/AuthManager.cpp). logOut on an unauthorized session destroys its keys and
    // closes TDLib; TdlibClient then starts a new instance, which asks for a phone number again.
    override suspend fun cancelQrCode(): Outcome<Unit> = client.authRequest(TdApi.LogOut()).map {}

    override suspend fun checkPassword(password: String): Outcome<Unit> =
        client.authRequest(TdApi.CheckAuthenticationPassword(password)).map {}

    override suspend fun requestPasswordRecovery(): Outcome<Unit> =
        client.authRequest(TdApi.RequestAuthenticationPasswordRecovery()).map {}

    override suspend fun checkPasswordRecoveryCode(code: String): Outcome<Unit> =
        client.authRequest(TdApi.CheckAuthenticationPasswordRecoveryCode(code)).map {}

    override suspend fun recoverPassword(
        code: String,
        newPassword: String,
        newHint: String,
    ): Outcome<Unit> = client.authRequest(TdApi.RecoverAuthenticationPassword(code, newPassword, newHint)).map {}

    override suspend fun register(
        firstName: String,
        lastName: String,
    ): Outcome<Unit> = client.authRequest(TdApi.RegisterUser(firstName, lastName, false)).map {}

    override suspend fun setEmailAddress(emailAddress: String): Outcome<Unit> =
        client.authRequest(TdApi.SetAuthenticationEmailAddress(emailAddress)).map {}

    override suspend fun checkEmailCode(code: String): Outcome<Unit> =
        client.authRequest(TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(code))).map {}

    override suspend fun resetEmailAddress(): Outcome<Unit> =
        client.authRequest(TdApi.ResetAuthenticationEmailAddress()).map {}

    override suspend fun logOut(): Outcome<Unit> = client.authRequest(TdApi.LogOut()).map {}
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

/** [TdlibClient.useServers], waiting as long as an authentication request; false when it took longer. */
private suspend fun TdlibClient.switchServers(servers: TdlibServers): Boolean =
    withTimeoutOrNull(AUTH_REQUEST_TIMEOUT) { useServers(servers) } != null

private fun TdlibServers.toTelegramServers() =
    when (this) {
        TdlibServers.Production -> TelegramServers.Production
        TdlibServers.Test -> TelegramServers.Test
    }

private fun TelegramServers.toTdlibServers() =
    when (this) {
        TelegramServers.Production -> TdlibServers.Production
        TelegramServers.Test -> TdlibServers.Test
    }

private suspend fun <T : TdApi.Object> TdlibClient.authRequest(function: TdApi.Function<T>) =
    request(function, AUTH_REQUEST_TIMEOUT)

/**
 * The TdApi object behind a state's timeouts. TDLib builds new objects for every state it reports, so the same
 * object means the same report, whether it came as an update or as the current state.
 */
private fun AuthState.timeoutPayload(): Any? =
    when (this) {
        is AuthState.WaitCode -> codeInfo
        is AuthState.WaitEmailCode -> codeInfo
        else -> null
    }

/** When a state report was first seen; a new report (another [markOf] key) starts a new mark. */
private class FirstSeen(
    private val timeSource: TimeSource.WithComparableMarks,
) {
    private var key: Any? = null
    private var mark: ComparableTimeMark? = null

    @Synchronized
    fun markOf(key: Any?): ComparableTimeMark {
        val known = mark?.takeIf { key != null && key === this.key }
        return known ?: timeSource.markNow().also {
            this.key = key
            mark = it
        }
    }
}

/** Marks the point of subscription in [TdlibAuthRepository.authStep]; never sent by TDLib. */
private object CurrentAuthState : TdApi.Update() {
    override fun getConstructor() = 0
}
