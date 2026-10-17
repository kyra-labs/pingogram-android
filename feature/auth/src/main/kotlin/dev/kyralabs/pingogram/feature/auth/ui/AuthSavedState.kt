package dev.kyralabs.pingogram.feature.auth.ui

import androidx.lifecycle.SavedStateHandle

/**
 * What survives process death (`docs/TESTING.md`): local navigation and what the user typed. TDLib persists its own
 * authorization state. Passwords are never saved: the saved state is kept outside the app process.
 */
internal fun SavedStateHandle.saveAuthModel(model: AuthModel) {
    this[KEY_WELCOME_DONE] = model.welcomeDone
    this[KEY_EDITING_PHONE] = model.editingPhone
    this[KEY_EDITING_EMAIL] = model.editingEmail
    this[KEY_CALLING_CODE] = model.phone.callingCode
    this[KEY_PHONE_NUMBER] = model.phone.number
    this[KEY_COUNTRY] = model.phone.countryIso
    this[KEY_CODE] = model.code.code
    this[KEY_RECOVERY_STAGE] = model.password.recovery.name
    this[KEY_RECOVERY_CODE] = model.password.recoveryCode
    this[KEY_FIRST_NAME] = model.registration.firstName
    this[KEY_LAST_NAME] = model.registration.lastName
    this[KEY_TERMS_ACCEPTED] = model.registration.termsAccepted
    this[KEY_EMAIL_ADDRESS] = model.email.emailAddress
    this[KEY_EMAIL_CODE] = model.email.code
}

internal fun SavedStateHandle.restoreAuthModel(): AuthModel {
    val recovery = RecoveryStage.entries.find { it.name == get<String>(KEY_RECOVERY_STAGE) } ?: RecoveryStage.None
    return AuthModel(
        welcomeDone = get<Boolean>(KEY_WELCOME_DONE) ?: false,
        editingPhone = get<Boolean>(KEY_EDITING_PHONE) ?: false,
        editingEmail = get<Boolean>(KEY_EDITING_EMAIL) ?: false,
        phone =
            PhoneForm(
                callingCode = get<String>(KEY_CALLING_CODE).orEmpty(),
                number = get<String>(KEY_PHONE_NUMBER).orEmpty(),
                countryIso = get<String>(KEY_COUNTRY),
            ),
        code = CodeForm(code = get<String>(KEY_CODE).orEmpty()),
        // Without the password a "new password" stage cannot continue; it resumes from the recovery code.
        password =
            PasswordForm(
                recovery = if (recovery == RecoveryStage.NewPassword) RecoveryStage.Code else recovery,
                recoveryCode = get<String>(KEY_RECOVERY_CODE).orEmpty(),
            ),
        registration =
            RegistrationForm(
                firstName = get<String>(KEY_FIRST_NAME).orEmpty(),
                lastName = get<String>(KEY_LAST_NAME).orEmpty(),
                termsAccepted = get<Boolean>(KEY_TERMS_ACCEPTED) ?: false,
            ),
        email =
            EmailForm(
                emailAddress = get<String>(KEY_EMAIL_ADDRESS).orEmpty(),
                code = get<String>(KEY_EMAIL_CODE).orEmpty(),
            ),
    )
}

private const val KEY_WELCOME_DONE = "auth.welcomeDone"
private const val KEY_EDITING_PHONE = "auth.editingPhone"
private const val KEY_EDITING_EMAIL = "auth.editingEmail"
private const val KEY_CALLING_CODE = "auth.callingCode"
private const val KEY_PHONE_NUMBER = "auth.phoneNumber"
private const val KEY_COUNTRY = "auth.country"
private const val KEY_CODE = "auth.code"
private const val KEY_RECOVERY_STAGE = "auth.recoveryStage"
private const val KEY_RECOVERY_CODE = "auth.recoveryCode"
private const val KEY_FIRST_NAME = "auth.firstName"
private const val KEY_LAST_NAME = "auth.lastName"
private const val KEY_TERMS_ACCEPTED = "auth.termsAccepted"
private const val KEY_EMAIL_ADDRESS = "auth.emailAddress"
private const val KEY_EMAIL_CODE = "auth.emailCode"
