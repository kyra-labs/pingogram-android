package dev.kyralabs.pingogram.core.domain.model

/**
 * The state of 2-step verification (`TwoStepVerificationRepository`): whether logging in on a new device also asks for
 * a password.
 *
 * `toString()` leaves the email address pattern out, so the state can be logged.
 *
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
) {
    override fun toString() =
        "TwoStepVerificationState(hasPassword=$hasPassword, hasRecoveryEmail=$hasRecoveryEmail, " +
            "pendingRecoveryEmail=${pendingRecoveryEmailPattern != null}, pendingResetDate=$pendingResetDate)"
}
