package dev.kyralabs.pingogram.feature.auth.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.ui.phone.PhoneNumberForm

/**
 * What survives process death (`docs/TESTING.md`): local navigation and what the user typed, except secrets. TDLib
 * persists its own authorization state. Passwords and codes (login, email and password recovery codes; a recovery code
 * is as good as the password) are never saved, because the saved state is kept outside the app process: the step comes
 * back with an empty field.
 */
internal fun SavedStateHandle.saveAuthModel(model: AuthModel) {
    this[KEY_WELCOME_DONE] = model.welcomeDone
    this[KEY_EDITING_PHONE] = model.editingPhone
    this[KEY_EDITING_EMAIL] = model.editingEmail
    this[KEY_CALLING_CODE] = model.phone.callingCode
    this[KEY_PHONE_NUMBER] = model.phone.number
    this[KEY_COUNTRY] = model.phone.countryIso
    this[KEY_RECOVERY_STAGE] = model.password.recovery.name
    this[KEY_FIRST_NAME] = model.registration.firstName
    this[KEY_LAST_NAME] = model.registration.lastName
    this[KEY_TERMS_ACCEPTED] = model.registration.termsAccepted
    this[KEY_EMAIL_ADDRESS] = model.email.emailAddress
    this[KEY_EMAIL_ADDRESS_ENTERED] = model.email.addressEntered
}

internal fun SavedStateHandle.restoreAuthModel(): AuthModel {
    val recovery = RecoveryStage.entries.find { it.name == get<String>(KEY_RECOVERY_STAGE) } ?: RecoveryStage.None
    return AuthModel(
        welcomeDone = get<Boolean>(KEY_WELCOME_DONE) ?: false,
        editingPhone = get<Boolean>(KEY_EDITING_PHONE) ?: false,
        editingEmail = get<Boolean>(KEY_EDITING_EMAIL) ?: false,
        phone =
            PhoneNumberForm(
                callingCode = get<String>(KEY_CALLING_CODE).orEmpty(),
                number = get<String>(KEY_PHONE_NUMBER).orEmpty(),
                countryIso = get<String>(KEY_COUNTRY),
            ),
        // The recovery code is not saved, so a "new password" stage resumes from entering the code.
        password = PasswordForm(recovery = if (recovery == RecoveryStage.NewPassword) RecoveryStage.Code else recovery),
        registration =
            RegistrationForm(
                firstName = get<String>(KEY_FIRST_NAME).orEmpty(),
                lastName = get<String>(KEY_LAST_NAME).orEmpty(),
                termsAccepted = get<Boolean>(KEY_TERMS_ACCEPTED) ?: false,
            ),
        email =
            EmailForm(
                emailAddress = get<String>(KEY_EMAIL_ADDRESS).orEmpty(),
                addressEntered = get<Boolean>(KEY_EMAIL_ADDRESS_ENTERED) ?: false,
            ),
    )
}

private const val KEY_WELCOME_DONE = "auth.welcomeDone"
private const val KEY_EDITING_PHONE = "auth.editingPhone"
private const val KEY_EDITING_EMAIL = "auth.editingEmail"
private const val KEY_CALLING_CODE = "auth.callingCode"
private const val KEY_PHONE_NUMBER = "auth.phoneNumber"
private const val KEY_COUNTRY = "auth.country"
private const val KEY_RECOVERY_STAGE = "auth.recoveryStage"
private const val KEY_FIRST_NAME = "auth.firstName"
private const val KEY_LAST_NAME = "auth.lastName"
private const val KEY_TERMS_ACCEPTED = "auth.termsAccepted"
private const val KEY_EMAIL_ADDRESS = "auth.emailAddress"
private const val KEY_EMAIL_ADDRESS_ENTERED = "auth.emailAddressEntered"
