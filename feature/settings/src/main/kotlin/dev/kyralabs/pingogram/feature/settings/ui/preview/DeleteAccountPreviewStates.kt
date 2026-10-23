package dev.kyralabs.pingogram.feature.settings.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.feature.settings.ui.account.DeleteAccountUiState
import dev.kyralabs.pingogram.feature.settings.ui.account.DeleteAccountViewModel

/** The page's states for previews and screenshot tests. */
internal object DeleteAccountPreviewStates {
    val withPassword =
        DeleteAccountUiState.Content(
            ttlDays = 180,
            ttlOptions = DeleteAccountViewModel.ttlOptions(180),
            hasPassword = true,
            passwordHint = "First pet",
        )
    val withoutPassword = withPassword.copy(hasPassword = false, passwordHint = "")
    val deleting = withoutPassword.copy(isDeleting = true)
    val confirming = withoutPassword.copy(confirming = true)
    val wrongPassword = withPassword.copy(passwordError = DomainError.PasswordInvalid)
    val floodWait = withPassword.copy(floodWaitSeconds = 42, floodWaitTotalSeconds = 60)
    val ownPeriod =
        withoutPassword.copy(ttlDays = 45, ttlOptions = DeleteAccountViewModel.ttlOptions(45))
}
