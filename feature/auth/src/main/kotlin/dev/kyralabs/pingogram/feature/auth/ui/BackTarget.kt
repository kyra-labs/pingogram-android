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
