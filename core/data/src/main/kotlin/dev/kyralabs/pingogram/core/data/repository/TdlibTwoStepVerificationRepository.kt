package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toTwoStepVerificationState
import dev.kyralabs.pingogram.core.domain.model.PasswordResetResult
import dev.kyralabs.pingogram.core.domain.model.TwoStepVerificationState
import dev.kyralabs.pingogram.core.domain.repository.TwoStepVerificationRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [TwoStepVerificationRepository] on TDLib's password requests, which ask Telegram each time (no cache, no updates).
 * Requests that check or change the password wait as long as authentication requests ([AUTH_REQUEST_TIMEOUT]): TDLib
 * waits out short `FLOOD_WAIT`s by itself, and giving up earlier would make a retry send another code.
 *
 * The current password is checked with `getRecoveryEmailAddress`, which needs it and changes nothing. A password with a
 * new recovery email takes effect once its code is confirmed: TDLib answers `setPassword` with the state waiting for
 * it rather than with Telegram's `EMAIL_UNCONFIRMED` error.
 *
 * One function per TDLib password request, as in [TwoStepVerificationRepository] (hence `TooManyFunctions`).
 */
@Suppress("TooManyFunctions")
@AccountScoped
class TdlibTwoStepVerificationRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : TwoStepVerificationRepository {
        override suspend fun state(): Outcome<TwoStepVerificationState> =
            client.request(TdApi.GetPasswordState()).map { it.toTwoStepVerificationState() }

        override suspend fun checkPassword(password: String): Outcome<Unit> =
            client.request(TdApi.GetRecoveryEmailAddress(password), AUTH_REQUEST_TIMEOUT).map {}

        override suspend fun setPassword(
            currentPassword: String?,
            newPassword: String,
            hint: String,
            recoveryEmail: String?,
        ): Outcome<TwoStepVerificationState> =
            changeState(
                TdApi.SetPassword(
                    currentPassword.orEmpty(),
                    newPassword,
                    hint,
                    recoveryEmail != null,
                    recoveryEmail.orEmpty(),
                ),
            )

        override suspend fun removePassword(currentPassword: String): Outcome<TwoStepVerificationState> =
            changeState(TdApi.SetPassword(currentPassword, "", "", true, ""))

        override suspend fun setRecoveryEmail(
            password: String,
            email: String,
        ): Outcome<TwoStepVerificationState> = changeState(TdApi.SetRecoveryEmailAddress(password, email))

        override suspend fun confirmRecoveryEmail(code: String): Outcome<TwoStepVerificationState> =
            changeState(TdApi.CheckRecoveryEmailAddressCode(code))

        override suspend fun resendRecoveryEmailCode(): Outcome<TwoStepVerificationState> =
            changeState(TdApi.ResendRecoveryEmailAddressCode())

        override suspend fun cancelRecoveryEmail(): Outcome<TwoStepVerificationState> =
            changeState(TdApi.CancelRecoveryEmailAddressVerification())

        override suspend fun requestRecovery(): Outcome<String> =
            client.request(TdApi.RequestPasswordRecovery(), AUTH_REQUEST_TIMEOUT).map { it.emailAddressPattern }

        override suspend fun checkRecoveryCode(code: String): Outcome<Unit> =
            client.request(TdApi.CheckPasswordRecoveryCode(code), AUTH_REQUEST_TIMEOUT).map {}

        override suspend fun recoverPassword(
            code: String,
            newPassword: String,
            hint: String,
        ): Outcome<TwoStepVerificationState> = changeState(TdApi.RecoverPassword(code, newPassword, hint))

        override suspend fun resetPassword(): Outcome<PasswordResetResult> =
            client.request(TdApi.ResetPassword(), AUTH_REQUEST_TIMEOUT).map { result ->
                when (result) {
                    is TdApi.ResetPasswordResultPending -> PasswordResetResult.Pending(result.pendingResetDate.toLong())
                    is TdApi.ResetPasswordResultDeclined -> PasswordResetResult.Declined(result.retryDate.toLong())
                    else -> PasswordResetResult.Done
                }
            }

        override suspend fun cancelReset(): Outcome<Unit> =
            client.request(TdApi.CancelPasswordReset(), AUTH_REQUEST_TIMEOUT).map {}

        private suspend fun changeState(function: TdApi.Function<TdApi.PasswordState>) =
            client.request(function, AUTH_REQUEST_TIMEOUT).map { it.toTwoStepVerificationState() }
    }
