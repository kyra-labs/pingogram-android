package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PasswordResetResult
import dev.kyralabs.pingogram.core.domain.model.TwoStepVerificationState

/**
 * 2-step verification of the account (`REQUIREMENTS.md` FR-S1, Privacy and security): the password Telegram asks for,
 * besides the code, when the account logs in on a new device, and the recovery email that resets it. A request that
 * gets no answer in time fails with `DomainError.Network`; a wrong password with `DomainError.PasswordInvalid`, a wrong
 * code with `DomainError.CodeInvalid`. Requests that change something answer with the state that results from them.
 * One function per step of the flows (hence `TooManyFunctions`).
 */
@Suppress("TooManyFunctions")
interface TwoStepVerificationRepository {
    /** Whether a password is set, with its recovery email, as Telegram reports it now. */
    suspend fun state(): Outcome<TwoStepVerificationState>

    /** Checks that [password] is the current password, without changing anything. */
    suspend fun checkPassword(password: String): Outcome<Unit>

    /**
     * Sets [newPassword] with [hint] in place of [currentPassword] (null while none is set). With a [recoveryEmail],
     * the password takes effect only once the code Telegram sends there is confirmed ([confirmRecoveryEmail]); the
     * resulting state then names the address in `pendingRecoveryEmailPattern`.
     */
    suspend fun setPassword(
        currentPassword: String?,
        newPassword: String,
        hint: String,
        recoveryEmail: String? = null,
    ): Outcome<TwoStepVerificationState>

    /** Turns 2-step verification off, which needs the [currentPassword]; the recovery email goes with it. */
    suspend fun removePassword(currentPassword: String): Outcome<TwoStepVerificationState>

    /** Sets [email] as the recovery email; it takes effect once the code Telegram sends there is confirmed. */
    suspend fun setRecoveryEmail(
        password: String,
        email: String,
    ): Outcome<TwoStepVerificationState>

    /** Confirms the recovery email waiting for its confirmation with the [code] Telegram sent there. */
    suspend fun confirmRecoveryEmail(code: String): Outcome<TwoStepVerificationState>

    /** Sends the confirmation code of the recovery email waiting for it again. */
    suspend fun resendRecoveryEmailCode(): Outcome<TwoStepVerificationState>

    /** Gives up the recovery email waiting for its confirmation, and a password waiting for it. */
    suspend fun cancelRecoveryEmail(): Outcome<TwoStepVerificationState>

    /**
     * Sends a code to the recovery email to reset a forgotten password; answers with the address as Telegram masks it.
     * Fails with `DomainError.PasswordRecoveryUnavailable` without a recovery email.
     */
    suspend fun requestRecovery(): Outcome<String>

    /** Checks the [code] sent by [requestRecovery], before the user chooses a new password. */
    suspend fun checkRecoveryCode(code: String): Outcome<Unit>

    /** Sets [newPassword] with [hint] in place of the forgotten one, with the [code] sent by [requestRecovery]. */
    suspend fun recoverPassword(
        code: String,
        newPassword: String,
        hint: String,
    ): Outcome<TwoStepVerificationState>

    /**
     * Resets a forgotten password without its recovery email: Telegram removes it after a waiting period, or at once
     * when asked again once that has passed.
     */
    suspend fun resetPassword(): Outcome<PasswordResetResult>

    /** Cancels a reset of the password that is waiting ([resetPassword]). */
    suspend fun cancelReset(): Outcome<Unit>
}
