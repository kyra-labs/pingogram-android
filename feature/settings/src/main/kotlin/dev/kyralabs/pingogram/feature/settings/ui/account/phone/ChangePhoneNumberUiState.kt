package dev.kyralabs.pingogram.feature.settings.ui.account.phone

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AuthCodeType
import dev.kyralabs.pingogram.core.ui.phone.CountryPickerUiState
import dev.kyralabs.pingogram.core.ui.phone.CountryUiModel

/** The steps of changing the number, one page each. */
enum class ChangePhoneNumberStep {
    /** What changing the number does, with the current number. */
    Intro,

    /** The new number: its country, calling code and national number. */
    Number,

    /** The code Telegram sent to the new number. */
    Code,

    /** The number changed. */
    Done,
}

/** The new number being typed, as the phone number fields show it (`core:ui`'s `PhoneNumberFields`). */
@Immutable
data class NewNumberUiState(
    val callingCode: String = "",
    val number: String = "",
    val numberFormat: String? = null,
    val country: CountryUiModel? = null,
    val error: DomainError? = null,
    val picker: CountryPickerUiState? = null,
)

/**
 * The code step: where Telegram sent the code ([sentTo], [type]), what was typed ([code], [error]), and a new code by
 * [nextType] once [resendInSeconds] reaches 0, out of [resendTotalSeconds].
 */
@Immutable
data class CodeUiState(
    val sentTo: String,
    val type: AuthCodeType,
    val nextType: AuthCodeType?,
    val code: String = "",
    val error: DomainError? = null,
    val resendInSeconds: Long = 0,
    val resendTotalSeconds: Long = 0,
) {
    val canResend: Boolean get() = nextType != null && resendInSeconds == 0L

    override fun toString() = "CodeUiState(type=$type, error=$error, resendInSeconds=$resendInSeconds)"
}

/**
 * Changing the account's phone number (`REQUIREMENTS.md` FR-S1): the [step] shown, the account's [currentNumber] (as
 * Telegram has it, digits), the [newNumber] typed, the [code] once Telegram sent one, and the number it became
 * ([changedTo]). While a request is on its way ([isWorking]) or Telegram asks to wait ([floodWaitSeconds], out of
 * [floodWaitTotalSeconds]), nothing can be sent.
 */
@Immutable
data class ChangePhoneNumberUiState(
    val step: ChangePhoneNumberStep = ChangePhoneNumberStep.Intro,
    val currentNumber: String = "",
    val newNumber: NewNumberUiState = NewNumberUiState(),
    val code: CodeUiState? = null,
    val changedTo: String? = null,
    val isWorking: Boolean = false,
    val floodWaitSeconds: Long? = null,
    val floodWaitTotalSeconds: Long = 0,
) {
    val canSubmit: Boolean get() = !isWorking && floodWaitSeconds == null

    /** Back returns to the step before rather than leaving: from the new number and the code. */
    val canGoBack: Boolean get() = step == ChangePhoneNumberStep.Number || step == ChangePhoneNumberStep.Code

    override fun toString() = "ChangePhoneNumberUiState(step=$step, code=$code, isWorking=$isWorking)"
}

/** What the change-number page does. */
sealed interface ChangePhoneNumberEvent {
    /** Leaves the explanation for the new number. */
    data object Start : ChangePhoneNumberEvent

    data class CallingCodeChanged(
        val input: String,
    ) : ChangePhoneNumberEvent

    data class NumberChanged(
        val input: String,
    ) : ChangePhoneNumberEvent

    data object OpenCountryPicker : ChangePhoneNumberEvent

    data class CountryQueryChanged(
        val query: String,
    ) : ChangePhoneNumberEvent

    data class CountrySelected(
        val isoCode: String,
    ) : ChangePhoneNumberEvent

    data object CloseCountryPicker : ChangePhoneNumberEvent

    data object SubmitNumber : ChangePhoneNumberEvent

    /** The code field changed; a complete code is submitted by itself. */
    data class CodeChanged(
        val input: String,
    ) : ChangePhoneNumberEvent

    data object SubmitCode : ChangePhoneNumberEvent

    data object ResendCode : ChangePhoneNumberEvent

    /** Back from the new number or the code: the step before. */
    data object Back : ChangePhoneNumberEvent
}

/** What the page shows once. */
sealed interface ChangePhoneNumberEffect {
    /** The number changed: a confirming buzz. */
    data object Changed : ChangePhoneNumberEffect

    /** Telegram refused what was typed: a rejecting buzz. */
    data object InputRejected : ChangePhoneNumberEffect

    data class ShowError(
        val error: DomainError,
    ) : ChangePhoneNumberEffect
}
