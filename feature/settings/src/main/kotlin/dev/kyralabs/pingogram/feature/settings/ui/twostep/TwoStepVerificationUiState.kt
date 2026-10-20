package dev.kyralabs.pingogram.feature.settings.ui.twostep

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError

/** What the 2-step verification page shows (`REQUIREMENTS.md` FR-S1). */
@Immutable
sealed interface TwoStepVerificationUiState {
    data object Loading : TwoStepVerificationUiState

    /** The state could not be loaded, because of [error]. */
    data class Error(
        val error: DomainError,
    ) : TwoStepVerificationUiState

    /**
     * The state loaded.
     *
     * @property hasPassword a password is set: 2-step verification is on.
     * @property hasRecoveryEmail a confirmed recovery email is set.
     * @property pendingEmailPattern the recovery email waiting for its confirmation code, as Telegram masks it; with no
     *   password, the password set with it waits too.
     * @property pendingReset a reset of a forgotten password that waits for Telegram's waiting period; null if none.
     * @property isWorking a cancellation or reset is on its way: the rows wait.
     */
    data class Content(
        val hasPassword: Boolean,
        val hasRecoveryEmail: Boolean,
        val pendingEmailPattern: String? = null,
        val pendingReset: PendingPasswordReset? = null,
        val isWorking: Boolean = false,
    ) : TwoStepVerificationUiState
}

/**
 * A requested reset of the password.
 *
 * @property date when Telegram lets it complete, formatted.
 * @property canCompleteNow that time has passed: resetting again removes the password at once.
 */
@Immutable
data class PendingPasswordReset(
    val date: String,
    val canCompleteNow: Boolean,
)

/** What the user does on the 2-step verification page, handled by [TwoStepVerificationViewModel.onEvent]. */
sealed interface TwoStepVerificationEvent {
    /** Load the state again after it failed. */
    data object Retry : TwoStepVerificationEvent

    /** The page shows again, e.g. back from a flow: load the state again, keeping what shows meanwhile. */
    data object Refresh : TwoStepVerificationEvent

    /** Give up the recovery email waiting for its code. */
    data object CancelRecoveryEmail : TwoStepVerificationEvent

    /** Cancel the requested reset of the password. */
    data object CancelReset : TwoStepVerificationEvent

    /** The waiting period is over and the user confirmed: remove the password now. */
    data object CompleteReset : TwoStepVerificationEvent
}

/** What the 2-step verification page asks its route to say once, in a snackbar. */
sealed interface TwoStepVerificationEffect {
    data class ShowError(
        val error: DomainError,
    ) : TwoStepVerificationEffect

    data object RecoveryEmailCancelled : TwoStepVerificationEffect

    data object ResetCancelled : TwoStepVerificationEffect

    /** The password was removed by the reset. */
    data object ResetDone : TwoStepVerificationEffect

    /** Telegram refused the reset; it can be asked again after [retryDate], formatted. */
    data class ResetDeclined(
        val retryDate: String,
    ) : TwoStepVerificationEffect
}
