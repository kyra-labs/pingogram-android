package dev.kyralabs.pingogram.feature.auth.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.model.Country
import dev.kyralabs.pingogram.core.domain.model.EmailAddressResetState
import dev.kyralabs.pingogram.core.domain.model.PhoneNumberInfo

/**
 * Everything [AuthViewModel] knows: TDLib's [step], what the user typed (kept across steps, so "change number" finds
 * the number again), and local navigation on top of TDLib's state. [toUiState] derives the screen from it.
 */
internal data class AuthModel(
    val step: AuthStep = AuthStep.Initializing,
    val connection: ConnectionStatus = ConnectionStatus.Connecting,
    /** "Start messaging" was tapped: phone number entry instead of the welcome screen. */
    val welcomeDone: Boolean = false,
    /** Phone number entry on top of a later TDLib step, to use another number. */
    val editingPhone: Boolean = false,
    /** Email address entry on top of the email code, to use another address. */
    val editingEmail: Boolean = false,
    /** QR login was left: TDLib restarts, and the flow continues with phone number entry. */
    val restarting: Boolean = false,
    val pendingAction: AuthAction? = null,
    val floodWaitSeconds: Long? = null,
    val phone: PhoneForm = PhoneForm(),
    val code: CodeForm = CodeForm(),
    val password: PasswordForm = PasswordForm(),
    val registration: RegistrationForm = RegistrationForm(),
    val email: EmailForm = EmailForm(),
)

internal data class PhoneForm(
    val countries: List<Country> = emptyList(),
    val callingCode: String = "",
    val number: String = "",
    val countryIso: String? = null,
    val numberFormat: String? = null,
    val error: DomainError? = null,
    val isPickerOpen: Boolean = false,
    val pickerQuery: String = "",
) {
    val country: Country? get() = countryIso?.let { iso -> countries.find { it.isoCode == iso } }

    /** The number as sent to TDLib: `+`, calling code and national number. */
    val internationalNumber: String get() = "+$callingCode$number"
}

internal data class CodeForm(
    val code: String = "",
    val error: DomainError? = null,
    val resendInSeconds: Long = 0,
    /** TDLib's raw number of the current code and its display form, once formatted. */
    val formattedPhoneNumber: Pair<String, String>? = null,
)

internal enum class RecoveryStage { None, Code, NewPassword }

internal data class PasswordForm(
    val password: String = "",
    val error: DomainError? = null,
    val showNoRecoveryEmailInfo: Boolean = false,
    val recovery: RecoveryStage = RecoveryStage.None,
    val recoveryCode: String = "",
    val recoveryCodeError: DomainError? = null,
    val newPassword: String = "",
    val newPasswordConfirmation: String = "",
    val newPasswordHint: String = "",
)

internal data class RegistrationForm(
    val firstName: String = "",
    val lastName: String = "",
    val firstNameError: DomainError? = null,
    val lastNameError: DomainError? = null,
    val termsAccepted: Boolean = false,
    /** Null until the step is shown; the terms then open by themselves if Telegram requires it. */
    val isTermsOpen: Boolean? = null,
)

internal data class EmailForm(
    val emailAddress: String = "",
    val error: DomainError? = null,
    val code: String = "",
    val codeError: DomainError? = null,
    /** Seconds left of a pending reset, counted down locally: TDLib reports it only once. */
    val resetInSeconds: Long? = null,
)

/** The model after TDLib reported [step]. Local navigation that no longer fits the new step is dropped. */
internal fun AuthModel.withStep(step: AuthStep): AuthModel {
    // The first step after the ViewModel was (re)created keeps the restored inputs and navigation.
    val keepLocal = this.step::class == step::class || this.step == AuthStep.Initializing
    val base = copy(step = step, editingPhone = editingPhone && keepLocal, editingEmail = editingEmail && keepLocal)
    return when (step) {
        AuthStep.WaitPhoneNumber -> base.atPhoneNumber()
        is AuthStep.WaitPassword -> if (keepLocal) base else base.copy(password = PasswordForm())
        is AuthStep.WaitRegistration -> base.withTermsOfService(step.termsOfService.mustShowPopup)
        is AuthStep.WaitEmailCode -> base.copy(email = email.copy(resetInSeconds = step.pendingResetSeconds()))
        AuthStep.Ready -> base.cleared()
        AuthStep.Closed -> if (restarting) base else base.cleared()
        else -> base
    }
}

/** The terms open by themselves the first time, when Telegram requires them and they are not accepted yet. */
private fun AuthModel.withTermsOfService(mustShowPopup: Boolean): AuthModel {
    val isOpen = registration.isTermsOpen ?: (mustShowPopup && !registration.termsAccepted)
    return copy(registration = registration.copy(isTermsOpen = isOpen))
}

private fun AuthStep.WaitEmailCode.pendingResetSeconds(): Long? =
    (resetState as? EmailAddressResetState.Pending)?.resetInSeconds?.toLong()

/** Nothing typed during a login outlives it; only the country list stays. */
private fun AuthModel.cleared() =
    AuthModel(step = step, connection = connection, phone = PhoneForm(countries = phone.countries))

/** Back at phone number entry: nothing is on top of it, and an earlier password attempt is gone. */
private fun AuthModel.atPhoneNumber() =
    copy(editingPhone = false, editingEmail = false, restarting = false, password = PasswordForm())

/**
 * The number format and country for TDLib's [info] about the current number. A country the user picked stays while
 * the number is still empty (+1 is both Canada and the United States); after that the number decides.
 */
internal fun PhoneForm.withPhoneNumberInfo(info: PhoneNumberInfo): PhoneForm {
    if (info.callingCode != callingCode) return copy(numberFormat = null)
    val detected = info.country?.isoCode
    val keepSelection = number.isEmpty() && country?.callingCodes?.contains(callingCode) == true
    return copy(
        numberFormat = numberFormatOf(info.formattedNumber),
        countryIso = if (detected == null || keepSelection) countryIso else detected,
    )
}

/** Errors shown next to a field (or handled by the screen's state); any other error goes to a snackbar. */
internal enum class ErrorField(
    val inlineErrors: Set<DomainError>,
) {
    None(emptySet()),
    Phone(setOf(DomainError.PhoneNumberInvalid, DomainError.PhoneNumberBanned, DomainError.PhoneNumberFlood)),
    Code(setOf(DomainError.CodeInvalid, DomainError.CodeExpired)),
    Password(setOf(DomainError.PasswordInvalid)),

    /** Asking for a recovery code: an account without a recovery email gets the explanation instead. */
    PasswordRecovery(setOf(DomainError.PasswordRecoveryUnavailable)),
    RecoveryCode(setOf(DomainError.CodeInvalid, DomainError.CodeExpired, DomainError.PasswordRecoveryExpired)),
    Registration(setOf(DomainError.FirstNameInvalid, DomainError.LastNameInvalid)),
    EmailAddress(setOf(DomainError.EmailInvalid, DomainError.EmailNotAllowed)),
    EmailCode(setOf(DomainError.CodeInvalid, DomainError.CodeExpired)),

    /** Requesting a reset that has to wait: TDLib answers "already requested" and reports the pending reset. */
    EmailReset(setOf(DomainError.AlreadyRequested)),
}

internal fun AuthModel.withFieldError(
    field: ErrorField,
    error: DomainError,
): AuthModel =
    when (field) {
        ErrorField.None -> {
            this
        }

        ErrorField.Phone -> {
            copy(phone = phone.copy(error = error))
        }

        ErrorField.Code -> {
            copy(code = code.copy(error = error))
        }

        ErrorField.Password -> {
            copy(password = password.copy(error = error))
        }

        ErrorField.PasswordRecovery -> {
            copy(password = password.copy(showNoRecoveryEmailInfo = true))
        }

        // Also when setting the new password: the code may have expired meanwhile.
        ErrorField.RecoveryCode -> {
            copy(password = password.copy(recovery = RecoveryStage.Code, recoveryCodeError = error))
        }

        ErrorField.EmailReset -> {
            this
        }

        ErrorField.EmailAddress -> {
            copy(email = email.copy(error = error))
        }

        ErrorField.EmailCode -> {
            copy(email = email.copy(codeError = error))
        }

        ErrorField.Registration -> {
            if (error == DomainError.LastNameInvalid) {
                copy(registration = registration.copy(lastNameError = error))
            } else {
                copy(registration = registration.copy(firstNameError = error))
            }
        }
    }

/** Where system back leads within the flow; null when back leaves it. */
internal enum class BackTarget {
    Welcome,
    CloseCountryPicker,
    StopChangingNumber,
    ChangeNumber,
    LeaveRecovery,
    CloseTermsOfService,
    StopChangingEmail,
    CancelQrCode,
}

internal fun AuthModel.backTarget(): BackTarget? =
    when {
        restarting -> null
        (step == AuthStep.WaitPhoneNumber || editingPhone) && phone.isPickerOpen -> BackTarget.CloseCountryPicker
        else -> stepBackTarget()
    }

private fun AuthModel.stepBackTarget(): BackTarget? =
    when (step) {
        AuthStep.Initializing, AuthStep.Closed, AuthStep.LoggingOut, AuthStep.Ready -> null
        AuthStep.WaitPhoneNumber -> BackTarget.Welcome.takeIf { welcomeDone }
        is AuthStep.WaitOtherDeviceConfirmation -> BackTarget.CancelQrCode
        else -> laterStepBackTarget()
    }

private fun AuthModel.laterStepBackTarget(): BackTarget =
    when {
        editingPhone -> BackTarget.StopChangingNumber
        step is AuthStep.WaitPassword && password.recovery != RecoveryStage.None -> BackTarget.LeaveRecovery
        step is AuthStep.WaitRegistration && registration.isTermsOpen == true -> BackTarget.CloseTermsOfService
        step is AuthStep.WaitEmailCode && editingEmail -> BackTarget.StopChangingEmail
        else -> BackTarget.ChangeNumber
    }
