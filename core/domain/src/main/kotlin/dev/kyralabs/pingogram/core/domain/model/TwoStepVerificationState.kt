package dev.kyralabs.pingogram.core.domain.model

/**
 * The state of 2-step verification (`TwoStepVerificationRepository`): whether logging in on a new device also asks for
 * a password.
 *
 * `toString()` leaves the hint and the email address pattern out, so the state can be logged.
 *
 * @property passwordHint the hint the user gave for the password; empty if none.
 * @property hasRecoveryEmail a confirmed recovery email address is set, so a forgotten password can be reset by email.
 * @property pendingRecoveryEmailPattern the recovery email address waiting for its confirmation code, as Telegram masks
 *   it ("a***@g***.com"); null if none is waiting.
 * @property pendingResetDate when a requested reset of the password can be completed (Unix time); null if none was
 *   requested.
 */
data class TwoStepVerificationState(
    val hasPassword: Boolean,
    val hasRecoveryEmail: Boolean,
    val pendingRecoveryEmailPattern: String? = null,
    val pendingResetDate: Long? = null,
    val passwordHint: String = "",
) {
    override fun toString() =
        "TwoStepVerificationState(hasPassword=$hasPassword, hasRecoveryEmail=$hasRecoveryEmail, " +
            "pendingRecoveryEmail=${pendingRecoveryEmailPattern != null}, pendingResetDate=$pendingResetDate, " +
            "hasHint=${passwordHint.isNotEmpty()})"
}

/** What Telegram did with a request to reset a forgotten password without its recovery email (`resetPassword`). */
sealed interface PasswordResetResult {
    /** The password was removed at once: 2-step verification is off. */
    data object Done : PasswordResetResult

    /** The password will be removed once [date] (Unix time) has passed, unless the user cancels it meanwhile. */
    data class Pending(
        val date: Long,
    ) : PasswordResetResult

    /** Telegram refused the reset, e.g. because one was cancelled recently; it can be asked again after [retryDate]. */
    data class Declined(
        val retryDate: Long,
    ) : PasswordResetResult
}
