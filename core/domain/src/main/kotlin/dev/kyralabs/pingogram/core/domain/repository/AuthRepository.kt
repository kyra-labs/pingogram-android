package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Login and logout (`REQUIREMENTS.md` §5.1). Each action answers once TDLib accepted or rejected it; the next step
 * arrives through [authStep]. Failures are typed (`DomainError`); a request that gets no answer in time fails with
 * `DomainError.Network`.
 *
 * One function per TDLib authentication request (td_api.tl), hence `TooManyFunctions` is suppressed: splitting them
 * would scatter one state machine.
 */
@Suppress("TooManyFunctions")
interface AuthRepository {
    /**
     * The current step first, then every step TDLib reports, in order, including the short-lived
     * [AuthStep.Closed]. A step can repeat with new data (a resent code, a new QR link).
     */
    val authStep: Flow<AuthStep>

    /** Sends a login code to [phoneNumber] (international format, `+` and digits). Also changes the number. */
    suspend fun setPhoneNumber(phoneNumber: String): Outcome<Unit>

    /** Sends a new login code the next way ([AuthStep.WaitCode.nextType]), or a new email code. */
    suspend fun resendCode(): Outcome<Unit>

    suspend fun checkCode(code: String): Outcome<Unit>

    /** Switches to QR code login: TDLib reports [AuthStep.WaitOtherDeviceConfirmation]. */
    suspend fun requestQrCode(): Outcome<Unit>

    /**
     * Leaves QR code login. TDLib offers no way back from it, so this ends the unauthorized session; TDLib starts
     * over and reports [AuthStep.WaitPhoneNumber].
     */
    suspend fun cancelQrCode(): Outcome<Unit>

    suspend fun checkPassword(password: String): Outcome<Unit>

    /** Emails a password recovery code to the account's recovery address. */
    suspend fun requestPasswordRecovery(): Outcome<Unit>

    /** Checks a password recovery code without using it. */
    suspend fun checkPasswordRecoveryCode(code: String): Outcome<Unit>

    /** Logs in with a valid recovery code and sets [newPassword] with [newHint]. */
    suspend fun recoverPassword(
        code: String,
        newPassword: String,
        newHint: String,
    ): Outcome<Unit>

    /** Creates the account; registering accepts Telegram's terms of service. */
    suspend fun register(
        firstName: String,
        lastName: String,
    ): Outcome<Unit>

    suspend fun setEmailAddress(emailAddress: String): Outcome<Unit>

    suspend fun checkEmailCode(code: String): Outcome<Unit>

    /** Resets the login email address, so the login continues with a code sent to the phone. */
    suspend fun resetEmailAddress(): Outcome<Unit>

    /** Logs out and deletes all local data; TDLib reports [AuthStep.LoggingOut], then [AuthStep.Closed]. */
    suspend fun logOut(): Outcome<Unit>
}

/**
 * Waits until TDLib reports whether the account is logged in: true for [AuthStep.Ready], false for any other step but
 * [AuthStep.Initializing]. For work that runs outside the app's screens (a notification action, a service): every
 * process starts TDLib in `PingogramApplication.onCreate`, before any worker, receiver or service runs, so this only
 * waits for TDLib to open its database. It never times out by itself.
 */
suspend fun AuthRepository.awaitLoggedIn(): Boolean = authStep.first { it != AuthStep.Initializing } == AuthStep.Ready
