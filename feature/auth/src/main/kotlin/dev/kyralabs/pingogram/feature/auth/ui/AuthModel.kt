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
    /** The country of the IP address was looked up for this session (once, and only for an empty calling code). */
    val isCountryDetected: Boolean = false,
) {
    val country: Country? get() = countryIso?.let { iso -> countries.find { it.isoCode == iso } }

    /** The number as sent to TDLib: `+`, calling code and national number. */
    val internationalNumber: String get() = "+$callingCode$number"
}

internal data class CodeForm(
    val code: String = "",
    val error: DomainError? = null,
    /** The last code Telegram rejected; it is not submitted again by itself. */
    val rejectedCode: String? = null,
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
    val newPasswordError: DomainError? = null,
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
    /**
     * The address was entered in this login. Only then can TDLib resend the email code: for an address the account
     * already had, it keeps none to resend to (td/telegram/AuthManager.cpp, `sentCodeTypeEmailCode`).
     */
    val addressEntered: Boolean = false,
    val code: String = "",
    val codeError: DomainError? = null,
    /** The last code Telegram rejected; it is not submitted again by itself. */
    val rejectedCode: String? = null,
    /** Seconds left of a pending reset, counted down to its deadline. */
    val resetInSeconds: Long? = null,
)

/** The model after TDLib reported [step]. Local navigation that no longer fits the new step is dropped. */
internal fun AuthModel.withStep(step: AuthStep): AuthModel {
    // The first step after the ViewModel was (re)created keeps the restored inputs and navigation.
    val keepLocal = this.step::class == step::class || this.step == AuthStep.Initializing
    val base =
        copy(
            step = step,
            editingPhone = editingPhone && keepLocal,
            editingEmail = editingEmail && keepLocal,
            email = if (step.isEmailLogin) email else email.copy(addressEntered = false),
        )
    return when (step) {
        AuthStep.WaitPhoneNumber -> base.atPhoneNumber()

        is AuthStep.WaitCode -> base.copy(code = base.code.copy(resendInSeconds = step.resendAvailableAt.secondsLeft()))

        is AuthStep.WaitPassword -> if (keepLocal) base else base.copy(password = PasswordForm())

        is AuthStep.WaitRegistration -> base.withTermsOfService(step.termsOfService.mustShowPopup)

        is AuthStep.WaitEmailCode -> base.copy(email = base.email.copy(resetInSeconds = step.resetSecondsLeft()))

        // While leaving QR login, TDLib is being logged out anyway: a late login must not count.
        AuthStep.Ready, AuthStep.Closed -> if (restarting) base else base.cleared()

        else -> base
    }
}

/** Steps of the email login, during which an address entered in it stays known (and TDLib starting up). */
private val AuthStep.isEmailLogin: Boolean
    get() = this == AuthStep.WaitEmailAddress || this is AuthStep.WaitEmailCode || this == AuthStep.Initializing

/** The terms open by themselves the first time, when Telegram requires them and they are not accepted yet. */
private fun AuthModel.withTermsOfService(mustShowPopup: Boolean): AuthModel {
    val isOpen = registration.isTermsOpen ?: (mustShowPopup && !registration.termsAccepted)
    return copy(registration = registration.copy(isTermsOpen = isOpen))
}

private fun AuthStep.WaitEmailCode.resetSecondsLeft(): Long? =
    (resetState as? EmailAddressResetState.Pending)?.resetAt?.secondsLeft()

/** Nothing typed during a login outlives it; only the country list stays (the current country is looked up again). */
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

    /** Setting the new password; an expired recovery code leads back to entering the code. */
    NewPassword(RecoveryCode.inlineErrors),
}

internal fun AuthModel.withFieldError(
    field: ErrorField,
    error: DomainError,
): AuthModel = if (error == DomainError.InputRejected) withRejectedInput(field) else withInlineError(field, error)

/**
 * A 406 during login: td_api.tl forbids showing or interpreting its message, so the field being submitted asks to
 * check the input instead.
 */
private fun AuthModel.withRejectedInput(field: ErrorField): AuthModel {
    val error = DomainError.InputRejected
    return when (field) {
        ErrorField.None -> this
        ErrorField.Phone -> copy(phone = phone.copy(error = error))
        ErrorField.Code -> copy(code = code.copy(error = error))
        ErrorField.Password, ErrorField.PasswordRecovery -> copy(password = password.copy(error = error))
        ErrorField.RecoveryCode -> copy(password = password.copy(recoveryCodeError = error))
        ErrorField.NewPassword -> copy(password = password.copy(newPasswordError = error))
        ErrorField.Registration -> copy(registration = registration.copy(firstNameError = error))
        ErrorField.EmailAddress -> copy(email = email.copy(error = error))
        ErrorField.EmailCode, ErrorField.EmailReset -> copy(email = email.copy(codeError = error))
    }
}

private fun AuthModel.withInlineError(
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
        ErrorField.RecoveryCode, ErrorField.NewPassword -> {
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
