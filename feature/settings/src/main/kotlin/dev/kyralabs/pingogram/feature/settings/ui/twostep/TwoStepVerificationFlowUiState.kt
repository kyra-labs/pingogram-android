package dev.kyralabs.pingogram.feature.settings.ui.twostep

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.input.ImeAction
import dev.kyralabs.pingogram.core.common.result.DomainError

/**
 * What a flow of 2-step verification does (`REQUIREMENTS.md` FR-S1), each a page pushed above the 2-step verification
 * page ([TwoStepVerificationFlowViewModel]). A forgotten password is a turn any flow that asks for the current password
 * can take, not a flow of its own.
 */
enum class TwoStepVerificationFlow {
    /** A first password: the password twice, a hint, and a recovery email or none, then its code. */
    SetPassword,

    /** The current password, then a new one twice and a hint; the recovery email stays. */
    ChangePassword,

    /** The current password turns 2-step verification off. */
    RemovePassword,

    /** The current password, then the new recovery email and its code. */
    ChangeRecoveryEmail,

    /** The code of the recovery email waiting for its confirmation. */
    ConfirmRecoveryEmail,
}

/** A step of a [TwoStepVerificationFlow]: one page of fields with the actions below them. */
enum class TwoStepVerificationStep {
    /** The current password, with its hint and "Forgot password?". */
    CurrentPassword,

    /** The new password, twice. */
    NewPassword,

    /** A hint for the new password, which may stay empty. */
    Hint,

    /** The recovery email address, which may be skipped while setting a first password. */
    RecoveryEmail,

    /** The code Telegram sent to the recovery email to confirm it. */
    EmailCode,

    /** The code Telegram sent to the recovery email to reset a forgotten password. */
    RecoveryCode,

    /** Without access to a recovery email: resetting the password after Telegram's waiting period. */
    Reset,
}

/** Why the field of a step is in the error state, said below it. */
@Immutable
sealed interface TwoStepVerificationFieldError {
    /** Telegram rejected what was entered: a wrong password or code, an expired code, an unusable address. */
    data class Rejected(
        val error: DomainError,
    ) : TwoStepVerificationFieldError

    /** The new password and its repetition differ. */
    data object PasswordsDiffer : TwoStepVerificationFieldError

    /** The hint is the password itself. */
    data object HintIsPassword : TwoStepVerificationFieldError
}

/**
 * What a flow of 2-step verification shows.
 *
 * @property recovering a forgotten password is being reset by email: the new password replaces it with the code.
 * @property passwordHint the hint of the current password, once loaded; empty if there is none.
 * @property emailPattern the recovery email address a code was sent to, as Telegram masks it ("a***@g***.com").
 * @property fieldError why the step's field is in the error state.
 * @property isWorking a request is on its way: the fields and actions wait.
 * @property floodWaitSeconds while Telegram asks to wait (`FLOOD_WAIT`), the whole seconds left; the step's main
 *   action waits meanwhile. [floodWaitTotalSeconds] is what the countdown started from.
 * @property canGoBack back returns to the step before rather than leaving the flow.
 */
@Immutable
data class TwoStepVerificationFlowUiState(
    val flow: TwoStepVerificationFlow,
    val step: TwoStepVerificationStep,
    val recovering: Boolean = false,
    val passwordHint: String = "",
    val emailPattern: String? = null,
    val fieldError: TwoStepVerificationFieldError? = null,
    val isWorking: Boolean = false,
    val floodWaitSeconds: Long? = null,
    val floodWaitTotalSeconds: Long = 0,
    val canGoBack: Boolean = false,
) {
    /** Whether the step's main action can be taken now. */
    val canSubmit: Boolean get() = !isWorking && floodWaitSeconds == null
}

/** What the user does in a flow, handled by [TwoStepVerificationFlowViewModel.onEvent]. */
sealed interface TwoStepVerificationFlowEvent {
    data class SubmitCurrentPassword(
        val password: String,
    ) : TwoStepVerificationFlowEvent

    data class SubmitNewPassword(
        val password: String,
        val repeated: String,
    ) : TwoStepVerificationFlowEvent

    data class SubmitHint(
        val hint: String,
    ) : TwoStepVerificationFlowEvent

    data class SubmitRecoveryEmail(
        val email: String,
    ) : TwoStepVerificationFlowEvent

    /** Sets a first password without a recovery email. */
    data object SkipRecoveryEmail : TwoStepVerificationFlowEvent

    data class SubmitEmailCode(
        val code: String,
    ) : TwoStepVerificationFlowEvent

    data object ResendEmailCode : TwoStepVerificationFlowEvent

    /** The current password is forgotten: a code to the recovery email, or a reset without one. */
    data object ForgotPassword : TwoStepVerificationFlowEvent

    data class SubmitRecoveryCode(
        val code: String,
    ) : TwoStepVerificationFlowEvent

    /** No access to the recovery email: offer the reset instead. */
    data object NoAccessToEmail : TwoStepVerificationFlowEvent

    /** The user confirmed resetting the password. */
    data object ConfirmReset : TwoStepVerificationFlowEvent

    /** What was typed changed: the field leaves its error state. */
    data object InputChanged : TwoStepVerificationFlowEvent

    /** Back to the step before ([TwoStepVerificationFlowUiState.canGoBack]). */
    data object Back : TwoStepVerificationFlowEvent
}

/** What a flow asks its route to do once. */
sealed interface TwoStepVerificationFlowEffect {
    /** The flow is done: leave it, and say [result] on the 2-step verification page. */
    data class Finished(
        val result: TwoStepVerificationResult,
    ) : TwoStepVerificationFlowEffect

    /** Telegram rejected what was entered: the route buzzes (`HapticFeedbackType.Reject`). */
    data object InputRejected : TwoStepVerificationFlowEffect

    data class ShowError(
        val error: DomainError,
    ) : TwoStepVerificationFlowEffect

    /** The confirmation code was sent again. */
    data object CodeResent : TwoStepVerificationFlowEffect

    /** Telegram refused the reset; it can be asked again after [retryDate], formatted. */
    data class ResetDeclined(
        val retryDate: String,
    ) : TwoStepVerificationFlowEffect
}

/** What a finished flow did, said in a snackbar on the 2-step verification page. */
enum class TwoStepVerificationResult {
    PasswordSet,
    PasswordChanged,
    PasswordRemoved,
    RecoveryEmailConfirmed,
    PasswordRecovered,
    ResetRequested,
    ResetDone,
}

/** What a field of a step is and how it submits. */
internal class TwoStepFieldSpec(
    val label: String,
    val error: String?,
    val enabled: Boolean,
    val imeAction: ImeAction,
    val onAction: () -> Unit,
)
