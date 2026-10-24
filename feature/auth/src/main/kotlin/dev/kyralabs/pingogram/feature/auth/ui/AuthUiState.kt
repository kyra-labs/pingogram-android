package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AuthCodeType
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.phone.CountryPickerUiState
import dev.kyralabs.pingogram.core.ui.phone.CountryUiModel
import kotlinx.collections.immutable.ImmutableList

/**
 * Everything the login screens show (`REQUIREMENTS.md` §5.1). [step] is the screen; the other properties apply to
 * every step.
 *
 * @property floodWaitSeconds while Telegram asks to wait (`FLOOD_WAIT`), the seconds left; submitting is disabled.
 * @property pendingAction the request in progress, if any; its button shows progress and every other action waits.
 * @property canGoBack whether system back is handled by the flow ([AuthEvent.Back]) rather than leaving it.
 * @property floodWaitTotalSeconds the whole flood wait, for its progress; 0 if unknown.
 * @property backStep the screen [AuthEvent.Back] leads to, which the predictive back gesture shows coming in; null
 *   when back leaves the flow or is not known.
 */
@Immutable
data class AuthUiState(
    val step: AuthStepUiState,
    val connection: ConnectionStatus,
    val floodWaitSeconds: Long?,
    val pendingAction: AuthAction?,
    val canGoBack: Boolean,
    val floodWaitTotalSeconds: Long = 0,
    val backStep: AuthStepUiState? = null,
) {
    /** Whether a request can be sent now: none is in progress and Telegram does not ask to wait. */
    val canSubmit: Boolean get() = pendingAction == null && floodWaitSeconds == null

    val isOffline: Boolean get() = connection == ConnectionStatus.WaitingForNetwork
}

/** A request the user started; the screen shows progress on the control that started it. */
enum class AuthAction {
    SubmitPhoneNumber,
    RequestQrCode,
    CancelQrCode,
    SubmitCode,
    ResendCode,
    SubmitPassword,
    RequestPasswordRecovery,
    SubmitRecoveryCode,
    SubmitNewPassword,
    SubmitRegistration,
    SubmitEmailAddress,
    SubmitEmailCode,
    ResendEmailCode,
    ResetEmailAddress,
}

/** One screen of the login flow. Field errors ([DomainError]) are shown with `errorText` from `core:ui`. */
@Immutable
sealed interface AuthStepUiState {
    /** TDLib is starting or restarting. */
    @Immutable
    data object Loading : AuthStepUiState

    /** FR-A1: brand, "Start messaging", links to the terms of service and the privacy policy. */
    @Immutable
    data object Welcome : AuthStepUiState

    /**
     * FR-A2: calling code and number, with live formatting ([numberFormat]) and a country picker.
     *
     * @property numberFormat how the national number is grouped: `-` is one digit, any other character a separator
     *   (from TDLib, e.g. `--- --- ----`); null if unknown. More digits than slots may follow.
     * @property isChangingNumber the user came back from a later step to use another number.
     * @property countryPicker the open country picker, or null.
     */
    @Immutable
    data class PhoneNumber(
        val callingCode: String,
        val number: String,
        val numberFormat: String?,
        val country: CountryUiModel?,
        val error: DomainError?,
        val isChangingNumber: Boolean,
        val countryPicker: CountryPickerUiState?,
    ) : AuthStepUiState {
        override fun toString() = "PhoneNumber(country=${country?.isoCode}, error=$error, …)"
    }

    /**
     * FR-A3: the login code. [codeLength] is null for codes without a fixed length (words, phrases); the code is
     * submitted by itself once it has [codeLength] characters. A new code can be requested by [nextType] once
     * [resendInSeconds] reaches 0, out of [resendTotalSeconds] (0 if unknown).
     */
    @Immutable
    data class Code(
        /** The number the code was sent to, formatted for display. */
        val phoneNumber: String,
        val type: AuthCodeType,
        val nextType: AuthCodeType?,
        val code: String,
        val codeLength: Int?,
        val resendInSeconds: Long,
        val error: DomainError?,
        val resendTotalSeconds: Long = 0,
    ) : AuthStepUiState {
        val canResend: Boolean get() = nextType != null && resendInSeconds == 0L

        override fun toString() =
            "Code(type=$type, nextType=$nextType, resendInSeconds=$resendInSeconds, error=$error, …)"
    }

    /**
     * FR-A4: the 2-step verification password. [showNoRecoveryEmailInfo]: the user asked to recover the password, but
     * the account has no recovery email; explain that it cannot be recovered here.
     */
    @Immutable
    data class Password(
        val hint: String,
        val password: String,
        val error: DomainError?,
        val showNoRecoveryEmailInfo: Boolean,
    ) : AuthStepUiState {
        override fun toString() = "Password(error=$error, showNoRecoveryEmailInfo=$showNoRecoveryEmailInfo, …)"
    }

    /** FR-A4, forgot password: the code emailed to [emailAddressPattern] (e.g. `a***@example.org`). */
    @Immutable
    data class PasswordRecoveryCode(
        val emailAddressPattern: String,
        val code: String,
        val error: DomainError?,
    ) : AuthStepUiState {
        override fun toString() = "PasswordRecoveryCode(error=$error, …)"
    }

    /** FR-A4, forgot password: a new password, entered twice, with an optional hint. Logs in when submitted. */
    @Immutable
    data class NewPassword(
        val password: String,
        val confirmation: String,
        val hint: String,
        /** Telegram rejected the new password ([DomainError.InputRejected]). */
        val error: DomainError? = null,
    ) : AuthStepUiState {
        /** Whether the confirmation differs from the password, once the user typed one. */
        val isMismatch: Boolean get() = confirmation.isNotEmpty() && confirmation != password

        val isComplete: Boolean get() = password.isNotEmpty() && password == confirmation

        override fun toString() = "NewPassword(isMismatch=$isMismatch, isComplete=$isComplete)"
    }

    /**
     * FR-A5: a new account. Registering requires [termsAccepted]; [isTermsOfServiceOpen] shows the terms, which open
     * by themselves when Telegram requires it.
     *
     * @property minUserAge the minimum age to accept the terms; 0 if there is none.
     */
    @Immutable
    data class Registration(
        val firstName: String,
        val lastName: String,
        val firstNameError: DomainError?,
        val lastNameError: DomainError?,
        val termsOfServiceText: String,
        val minUserAge: Int,
        val termsAccepted: Boolean,
        val isTermsOfServiceOpen: Boolean,
    ) : AuthStepUiState

    /** FR-A6: QR code login; another device scans [link] (`tg://login?token=…`), which TDLib renews often. */
    @Immutable
    data class QrCode(
        val link: String,
    ) : AuthStepUiState {
        override fun toString() = "QrCode(…)"
    }

    /** FR-A7: Telegram requires an email address for this account. */
    @Immutable
    data class EmailAddress(
        val emailAddress: String,
        val error: DomainError?,
        /** The user came back from the email code to use another address. */
        val isChangingAddress: Boolean,
    ) : AuthStepUiState {
        override fun toString() = "EmailAddress(error=$error, isChangingAddress=$isChangingAddress, …)"
    }

    /** FR-A7: the code emailed to [emailAddressPattern]; submitted by itself once it has [codeLength] digits. */
    @Immutable
    data class EmailCode(
        val emailAddressPattern: String,
        val code: String,
        val codeLength: Int,
        val error: DomainError?,
        /** Whether, and when, the email address can be reset to continue with a code sent to the phone. */
        val reset: EmailResetUiState?,
        /**
         * Whether a new code can be requested: only for an address entered in this login. TDLib keeps none to resend to
         * for an address the account already had.
         */
        val canResendCode: Boolean = false,
    ) : AuthStepUiState {
        override fun toString() =
            "EmailCode(codeLength=$codeLength, error=$error, reset=$reset, canResendCode=$canResendCode, …)"
    }

    /**
     * Telegram lets this account log in only after buying Telegram Premium in an app store. Pingogram explains this and
     * offers the support address; it never offers or bypasses the purchase (`REQUIREMENTS.md` §1.2).
     */
    @Immutable
    data class PremiumRequired(
        val supportEmailAddress: String,
    ) : AuthStepUiState

    /** FR-A8: logging out; TDLib starts over afterwards. */
    @Immutable
    data object LoggingOut : AuthStepUiState

    /** Logged in; [AuthEffect.Authorized] tells the app to leave the login flow. */
    @Immutable
    data object Done : AuthStepUiState
}

@Immutable
sealed interface EmailResetUiState {
    /** A reset can be requested; it takes [waitSeconds] (0: immediately). */
    @Immutable
    data class Available(
        val waitSeconds: Long,
    ) : EmailResetUiState

    /** A reset was requested and happens in [resetInSeconds]; requesting again checks whether it can happen now. */
    @Immutable
    data class Pending(
        val resetInSeconds: Long,
    ) : EmailResetUiState
}
