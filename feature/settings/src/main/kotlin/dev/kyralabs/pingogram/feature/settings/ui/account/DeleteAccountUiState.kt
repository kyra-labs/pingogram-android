package dev.kyralabs.pingogram.feature.settings.ui.account

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.ImmutableList

/** What the Delete account page shows (`docs/PLAY_STORE.md`, account deletion). */
@Immutable
sealed interface DeleteAccountUiState {
    data object Loading : DeleteAccountUiState

    /** Nothing could be loaded, because of [error]. */
    data class Error(
        val error: DomainError,
    ) : DeleteAccountUiState

    /**
     * Everything loaded.
     *
     * @property ttlDays how many days without coming online Telegram waits before it deletes the account; while
     *   [savingTtl], the choice on its way.
     * @property ttlOptions the choices offered for [ttlDays], in days, shortest first; [ttlDays] among them.
     * @property hasPassword whether 2-step verification is on, so deleting needs its password.
     * @property passwordHint the password's hint, empty for none.
     * @property confirming whether the destructive confirmation shows.
     * @property isDeleting whether the deletion is on its way, or done and the logout follows.
     * @property passwordError why Telegram refused the password, shown under its field.
     * @property floodWaitSeconds the seconds left while Telegram asks to wait (`FLOOD_WAIT`), else null.
     * @property floodWaitTotalSeconds the whole wait, for the countdown's indicator.
     */
    data class Content(
        val ttlDays: Int,
        val ttlOptions: ImmutableList<Int>,
        val hasPassword: Boolean,
        val passwordHint: String = "",
        val savingTtl: Boolean = false,
        val confirming: Boolean = false,
        val isDeleting: Boolean = false,
        val passwordError: DomainError? = null,
        val floodWaitSeconds: Long? = null,
        val floodWaitTotalSeconds: Long = 0,
    ) : DeleteAccountUiState {
        /** Whether "Delete account" may be pressed, given whether the password field is filled where it is needed. */
        fun canDelete(passwordEntered: Boolean): Boolean =
            !isDeleting && floodWaitSeconds == null && (!hasPassword || passwordEntered)
    }
}

/** What the user does on the Delete account page, handled by [DeleteAccountViewModel.onEvent]. */
sealed interface DeleteAccountEvent {
    /** Load the page again after it failed. */
    data object Retry : DeleteAccountEvent

    /** Choose how many [days] the account may stay unused. */
    data class SelectTtl(
        val days: Int,
    ) : DeleteAccountEvent

    /** "Delete account": ask before deleting. */
    data object RequestDeletion : DeleteAccountEvent

    /** The confirmation closed without deleting. */
    data object DismissConfirmation : DeleteAccountEvent

    /** The confirmation's "Delete": delete with the [reason] typed (empty for none) and the [password] typed. */
    data class ConfirmDeletion(
        val reason: String,
        val password: String,
    ) : DeleteAccountEvent

    /** The password's text changed: its error goes. */
    data object PasswordChanged : DeleteAccountEvent
}

/** What the Delete account page does once. */
sealed interface DeleteAccountEffect {
    /** Telegram refused the password: buzz (`docs/DESIGN_SYSTEM.md` §5, `Reject`). */
    data object PasswordRejected : DeleteAccountEffect

    /** The account is deleted; TDLib logs out next (`Confirm`). */
    data object Deleted : DeleteAccountEffect

    /** A failure the page shows in a snackbar. */
    data class ShowError(
        val error: DomainError,
    ) : DeleteAccountEffect
}
