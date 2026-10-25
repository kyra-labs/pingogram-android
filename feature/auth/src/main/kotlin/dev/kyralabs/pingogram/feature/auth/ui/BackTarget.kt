package dev.kyralabs.pingogram.feature.auth.ui

import dev.kyralabs.pingogram.core.domain.model.AuthStep

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

    /** Leaves an added account's sign-in; the app opens again on the account shown before. */
    LeaveAddingAccount,
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
        AuthStep.WaitPhoneNumber ->
            if (addingAccount) BackTarget.LeaveAddingAccount else BackTarget.Welcome.takeIf { welcomeDone }
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

/**
 * The model after going back to [target], without the requests some targets start ([AuthViewModel] sends them): the
 * view model applies it, and the UI state derives the screen back leads to from it ([AuthUiState.backStep]).
 */
internal fun AuthModel.afterBack(target: BackTarget): AuthModel =
    when (target) {
        BackTarget.Welcome -> copy(welcomeDone = false, phone = phone.copy(error = null))

        BackTarget.CloseCountryPicker -> copy(phone = phone.copy(isPickerOpen = false, pickerQuery = ""))

        BackTarget.StopChangingNumber -> copy(editingPhone = false)

        BackTarget.ChangeNumber -> copy(editingPhone = true, phone = phone.copy(error = null))

        BackTarget.LeaveRecovery -> copy(password = PasswordForm(password = password.password))

        BackTarget.CloseTermsOfService -> copy(registration = registration.copy(isTermsOpen = false))

        BackTarget.StopChangingEmail -> copy(editingEmail = false)

        // TDLib restarts (AuthViewModel cancels the QR login), and the flow continues with phone number entry.
        BackTarget.CancelQrCode -> copy(restarting = true, pendingAction = AuthAction.CancelQrCode)

        // The flow itself does not move: the app leaves it (AuthViewModel hands it to the device's accounts).
        BackTarget.LeaveAddingAccount -> this
    }
