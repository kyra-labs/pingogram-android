package dev.kyralabs.pingogram.feature.settings.ui.account.email

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.feature.settings.ui.account.FlowStepState

/** The steps of changing the login email, one page each. */
enum class LoginEmailStep {
    /** The current login email and what it is for. */
    Intro,

    /** The new address. */
    Address,

    /** The code Telegram sent to the new address. */
    Code,

    /** The login email changed. */
    Done,
}

/**
 * Changing the login email (`REQUIREMENTS.md` FR-S1): the [step] shown; the [current] address as Telegram's pattern of
 * it (null while [isLoading], or when the account has none, which Telegram lets change only once it set one), or why
 * it could not be read ([loadError]); the [address] typed and Telegram's refusal of it; the [code] once Telegram sent
 * one ([sentTo], [codeLength]: 0 when unknown).
 */
@Immutable
data class LoginEmailUiState(
    val step: LoginEmailStep = LoginEmailStep.Intro,
    val isLoading: Boolean = true,
    val loadError: DomainError? = null,
    val current: String? = null,
    val address: String = "",
    val addressError: DomainError? = null,
    val sentTo: String = "",
    val codeLength: Int = 0,
    val code: String = "",
    val codeError: DomainError? = null,
    override val isWorking: Boolean = false,
    override val floodWaitSeconds: Long? = null,
    override val floodWaitTotalSeconds: Long = 0,
) : FlowStepState {
    /** Back returns to the step before rather than leaving: from the address and the code. */
    val canGoBack: Boolean get() = step == LoginEmailStep.Address || step == LoginEmailStep.Code

    override fun toString() = "LoginEmailUiState(step=$step, isLoading=$isLoading, hasCurrent=${current != null})"
}

/** What the login email page does. */
sealed interface LoginEmailEvent {
    data object Retry : LoginEmailEvent

    /** Leaves the explanation for the new address. */
    data object Start : LoginEmailEvent

    data class AddressChanged(
        val input: String,
    ) : LoginEmailEvent

    data object SubmitAddress : LoginEmailEvent

    /** The code field changed; a complete code is submitted by itself. */
    data class CodeChanged(
        val input: String,
    ) : LoginEmailEvent

    data object SubmitCode : LoginEmailEvent

    data object ResendCode : LoginEmailEvent

    data object Back : LoginEmailEvent
}

/** What the page shows once. */
sealed interface LoginEmailEffect {
    /** The login email changed: a confirming buzz. */
    data object Changed : LoginEmailEffect

    /** Telegram refused what was typed: a rejecting buzz. */
    data object InputRejected : LoginEmailEffect

    /** A new code is on its way. */
    data object CodeResent : LoginEmailEffect

    data class ShowError(
        val error: DomainError,
    ) : LoginEmailEffect
}
