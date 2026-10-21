package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError

/** How a new contact is found: by phone number, or by Telegram username. */
enum class ContactMethod {
    PhoneNumber,
    Username,
}

/** A field of the contact forms. */
enum class ContactField {
    FirstName,
    LastName,
    PhoneNumber,
    Username,
}

/**
 * What is wrong with [field]: what Telegram answered ([error]), or, without one, what the form found itself (a first
 * name missing, a phone number without its country code).
 */
@Immutable
data class ContactFieldError(
    val field: ContactField,
    val error: DomainError? = null,
)

/**
 * The Add contact screen (`REQUIREMENTS.md` FR-P1): the names and the phone number or the username ([method]) as typed,
 * the country the number belongs to as far as its digits tell ([country]), what a field has wrong ([fieldError]), and
 * the request on its way ([isWorking]) or the `FLOOD_WAIT` counting down ([floodWaitSeconds], from
 * [floodWaitTotalSeconds]). [notOnTelegram]: no one uses Telegram with the number, so the screen offers to invite them.
 */
@Immutable
data class NewContactUiState(
    val method: ContactMethod = ContactMethod.PhoneNumber,
    val firstName: String = "",
    val lastName: String = "",
    val phoneNumber: String = "",
    val username: String = "",
    val country: String? = null,
    val fieldError: ContactFieldError? = null,
    val isWorking: Boolean = false,
    val floodWaitSeconds: Long? = null,
    val floodWaitTotalSeconds: Long = 0,
    val notOnTelegram: Boolean = false,
) {
    /** Whether "Add" may be tapped: what the method needs is there, and nothing is on its way or counting down. */
    val canSubmit: Boolean
        get() {
            val filled =
                when (method) {
                    ContactMethod.PhoneNumber -> firstName.isNotBlank() && phoneNumber.any(Char::isDigit)
                    ContactMethod.Username -> username.isNotBlank()
                }
            return filled && !isWorking && floodWaitSeconds == null
        }

    override fun toString() =
        "NewContactUiState(method=$method, fieldError=$fieldError, isWorking=$isWorking, notOnTelegram=$notOnTelegram)"
}

/** What the user does on the Add contact screen, sent to [NewContactViewModel.onEvent]. */
sealed interface NewContactEvent {
    data class MethodChanged(
        val method: ContactMethod,
    ) : NewContactEvent

    data class FirstNameChanged(
        val value: String,
    ) : NewContactEvent

    data class LastNameChanged(
        val value: String,
    ) : NewContactEvent

    data class PhoneNumberChanged(
        val value: String,
    ) : NewContactEvent

    data class UsernameChanged(
        val value: String,
    ) : NewContactEvent

    /** "Add". */
    data object Submit : NewContactEvent

    /** "Invite" for a number that is not on Telegram. */
    data object Invite : NewContactEvent

    /** "Change number" for a number that is not on Telegram, or back from there: the form again. */
    data object ChangeNumber : NewContactEvent
}

/** One-off effects of [NewContactViewModel], handled by the screen once each. */
sealed interface NewContactEffect {
    /** The contact was added: the screen closes. */
    data class Added(
        val userId: Long,
    ) : NewContactEffect

    /** Opens the share sheet with an invitation to Telegram that carries [link]. */
    data class ShareInvite(
        val link: String,
    ) : NewContactEffect

    /** Something failed with [error] that no field shows: a snackbar says so. */
    data class ShowError(
        val error: DomainError,
    ) : NewContactEffect
}
