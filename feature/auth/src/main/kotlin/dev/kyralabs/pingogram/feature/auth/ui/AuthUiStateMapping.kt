package dev.kyralabs.pingogram.feature.auth.ui

import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.model.Country
import dev.kyralabs.pingogram.core.domain.model.EmailAddressResetState
import dev.kyralabs.pingogram.core.domain.model.codeLength
import dev.kyralabs.pingogram.core.ui.phone.CountryPickerUiState
import dev.kyralabs.pingogram.core.ui.phone.matching
import dev.kyralabs.pingogram.core.ui.phone.toUiModel
import dev.kyralabs.pingogram.core.ui.time.secondsLeft
import kotlinx.collections.immutable.toImmutableList

/** [AuthModel] → [AuthUiState]: which screen to show, and what it shows. */
internal fun AuthModel.toUiState(): AuthUiState {
    val backTarget = backTarget()
    return AuthUiState(
        step = screen(),
        connection = connection,
        floodWaitSeconds = floodWaitSeconds,
        pendingAction = pendingAction,
        canGoBack = backTarget != null,
        floodWaitTotalSeconds = floodWaitTotalSeconds,
        // The same reduction as AuthViewModel's back, so the screen the gesture shows is the one it leads to.
        backStep = backTarget?.let { afterBack(it).screen() },
    )
}

private fun AuthModel.screen(): AuthStepUiState {
    val step = step
    return when {
        restarting || step == AuthStep.Initializing || step == AuthStep.Closed -> AuthStepUiState.Loading
        step == AuthStep.WaitPhoneNumber && !welcomeDone -> AuthStepUiState.Welcome
        step == AuthStep.WaitPhoneNumber -> phoneScreen(isChangingNumber = false)
        step is AuthStep.WaitOtherDeviceConfirmation -> AuthStepUiState.QrCode(step.link)
        step == AuthStep.LoggingOut -> AuthStepUiState.LoggingOut
        step == AuthStep.Ready -> AuthStepUiState.Done
        editingPhone -> phoneScreen(isChangingNumber = true)
        else -> laterStepScreen(step)
    }
}

private fun AuthModel.laterStepScreen(step: AuthStep): AuthStepUiState =
    when (step) {
        is AuthStep.WaitCode -> {
            AuthStepUiState.Code(
                phoneNumber = displayPhoneNumber(step.phoneNumber),
                type = step.type,
                nextType = step.nextType,
                code = code.code,
                codeLength = step.type.codeLength,
                resendInSeconds = code.resendInSeconds,
                error = code.error,
                resendTotalSeconds = code.resendTotalSeconds,
            )
        }

        is AuthStep.WaitPassword -> {
            passwordScreen(step)
        }

        is AuthStep.WaitRegistration -> {
            AuthStepUiState.Registration(
                firstName = registration.firstName,
                lastName = registration.lastName,
                firstNameError = registration.firstNameError,
                lastNameError = registration.lastNameError,
                termsOfServiceText = step.termsOfService.text,
                minUserAge = step.termsOfService.minUserAge,
                termsAccepted = registration.termsAccepted,
                isTermsOfServiceOpen = registration.isTermsOpen == true,
            )
        }

        AuthStep.WaitEmailAddress -> {
            AuthStepUiState.EmailAddress(email.emailAddress, email.error, isChangingAddress = false)
        }

        is AuthStep.WaitEmailCode -> {
            if (editingEmail) {
                AuthStepUiState.EmailAddress(email.emailAddress, email.error, isChangingAddress = true)
            } else {
                AuthStepUiState.EmailCode(
                    emailAddressPattern = step.emailAddressPattern,
                    code = email.code,
                    codeLength = step.codeLength,
                    error = email.codeError,
                    reset = step.resetState?.toUiState(email.resetInSeconds),
                    canResendCode = email.addressEntered,
                )
            }
        }

        is AuthStep.WaitPremiumPurchase -> {
            AuthStepUiState.PremiumRequired(step.supportEmailAddress)
        }

        else -> {
            AuthStepUiState.Loading
        }
    }

private fun AuthModel.passwordScreen(step: AuthStep.WaitPassword): AuthStepUiState =
    when (password.recovery) {
        RecoveryStage.None -> {
            AuthStepUiState.Password(
                hint = step.hint,
                password = password.password,
                error = password.error,
                showNoRecoveryEmailInfo = password.showNoRecoveryEmailInfo,
            )
        }

        RecoveryStage.Code -> {
            AuthStepUiState.PasswordRecoveryCode(
                emailAddressPattern = step.recoveryEmailAddressPattern,
                code = password.recoveryCode,
                error = password.recoveryCodeError,
            )
        }

        RecoveryStage.NewPassword -> {
            AuthStepUiState.NewPassword(
                password = password.newPassword,
                confirmation = password.newPasswordConfirmation,
                hint = password.newPasswordHint,
                error = password.newPasswordError,
            )
        }
    }

private fun AuthModel.phoneScreen(isChangingNumber: Boolean) =
    AuthStepUiState.PhoneNumber(
        callingCode = phone.callingCode,
        number = phone.number,
        numberFormat = phone.numberFormat,
        country = phone.country?.toUiModel(),
        error = phone.error,
        isChangingNumber = isChangingNumber,
        countryPicker = phone.picker,
    )

private fun AuthModel.displayPhoneNumber(raw: String): String =
    code.formattedPhoneNumber?.takeIf { it.first == raw }?.second ?: "+${raw.filter(Char::isDigit)}"

private fun EmailAddressResetState.toUiState(resetInSeconds: Long?): EmailResetUiState =
    when (this) {
        is EmailAddressResetState.Available -> EmailResetUiState.Available(waitPeriodSeconds.toLong())
        is EmailAddressResetState.Pending -> EmailResetUiState.Pending(resetInSeconds ?: resetAt.secondsLeft())
    }
