package dev.kyralabs.pingogram.feature.auth.ui

/**
 * What the user does on the login screens, sent to [AuthViewModel.onEvent]. Events carrying what the user typed
 * leave it out of `toString()`, so they can be logged.
 */
sealed interface AuthEvent {
    /** System back while [AuthUiState.canGoBack]: goes back one step within the flow. */
    data object Back : AuthEvent

    // Welcome

    data object StartMessaging : AuthEvent

    data object OpenTermsOfService : AuthEvent

    data object OpenPrivacyPolicy : AuthEvent

    // Phone number

    /** Digits of the calling code; a whole international number is split into code and number. */
    data class CallingCodeChanged(
        val callingCode: String,
    ) : AuthEvent {
        override fun toString() = "CallingCodeChanged(…)"
    }

    /** The national number as typed or pasted; separators are dropped, and a pasted `+…` number is split. */
    data class PhoneNumberChanged(
        val phoneNumber: String,
    ) : AuthEvent {
        override fun toString() = "PhoneNumberChanged(…)"
    }

    data object OpenCountryPicker : AuthEvent

    data class CountryQueryChanged(
        val query: String,
    ) : AuthEvent

    data class CountrySelected(
        val isoCode: String,
    ) : AuthEvent

    data object CloseCountryPicker : AuthEvent

    data object SubmitPhoneNumber : AuthEvent

    /** FR-A6: log in by scanning a QR code with another device instead. */
    data object LogInWithQrCode : AuthEvent

    // QR code

    /** Back to phone number entry. */
    data object CancelQrCode : AuthEvent

    // Login code

    /** The code as typed or pasted; non-digits are dropped from digit codes. Submits once complete. */
    data class CodeChanged(
        val code: String,
    ) : AuthEvent {
        override fun toString() = "CodeChanged(…)"
    }

    data object SubmitCode : AuthEvent

    data object ResendCode : AuthEvent

    /** Back to phone number entry, to use another number. */
    data object ChangePhoneNumber : AuthEvent

    /** Opens fragment.com, where codes for numbers bought there are shown. */
    data object OpenFragment : AuthEvent

    // 2-step verification password

    data class PasswordChanged(
        val password: String,
    ) : AuthEvent {
        override fun toString() = "PasswordChanged(…)"
    }

    data object SubmitPassword : AuthEvent

    data object ForgotPassword : AuthEvent

    data class RecoveryCodeChanged(
        val code: String,
    ) : AuthEvent {
        override fun toString() = "RecoveryCodeChanged(…)"
    }

    data object SubmitRecoveryCode : AuthEvent

    data class NewPasswordChanged(
        val password: String,
    ) : AuthEvent {
        override fun toString() = "NewPasswordChanged(…)"
    }

    data class NewPasswordConfirmationChanged(
        val confirmation: String,
    ) : AuthEvent {
        override fun toString() = "NewPasswordConfirmationChanged(…)"
    }

    data class NewPasswordHintChanged(
        val hint: String,
    ) : AuthEvent {
        override fun toString() = "NewPasswordHintChanged(…)"
    }

    data object SubmitNewPassword : AuthEvent

    // Registration

    data class FirstNameChanged(
        val firstName: String,
    ) : AuthEvent {
        override fun toString() = "FirstNameChanged(…)"
    }

    data class LastNameChanged(
        val lastName: String,
    ) : AuthEvent {
        override fun toString() = "LastNameChanged(…)"
    }

    data class TermsAcceptedChanged(
        val accepted: Boolean,
    ) : AuthEvent

    /** Shows the full terms of service. */
    data object ShowTermsOfService : AuthEvent

    data object DismissTermsOfService : AuthEvent

    data object AcceptTermsOfService : AuthEvent

    data object SubmitRegistration : AuthEvent

    // Email address

    data class EmailAddressChanged(
        val emailAddress: String,
    ) : AuthEvent {
        override fun toString() = "EmailAddressChanged(…)"
    }

    data object SubmitEmailAddress : AuthEvent

    /** Back from the email code to enter another address. */
    data object ChangeEmailAddress : AuthEvent

    data class EmailCodeChanged(
        val code: String,
    ) : AuthEvent {
        override fun toString() = "EmailCodeChanged(…)"
    }

    data object SubmitEmailCode : AuthEvent

    data object ResendEmailCode : AuthEvent

    data object ResetEmailAddress : AuthEvent

    // Telegram Premium required

    /** Writes to Telegram's support address about the required purchase. */
    data object ContactSupport : AuthEvent
}
