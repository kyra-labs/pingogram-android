package dev.kyralabs.pingogram.core.domain.model

/** What came of adding a contact by phone number (`REQUIREMENTS.md` FR-P1, `ContactRepository.addByPhoneNumber`). */
sealed interface AddContactResult {
    /** The number belongs to Telegram user [userId], now in the contacts. */
    data class Added(
        val userId: Long,
    ) : AddContactResult

    /** No one uses Telegram with the number: the user can invite its owner instead. */
    data object NotOnTelegram : AddContactResult
}

/**
 * A Telegram user the current user edits as a contact, or adds to the contacts (`ContactRepository.editableContact`).
 *
 * @property user the user as TDLib has it; for a contact, its names are those the current user gave it.
 * @property canSharePhoneNumber the user cannot see the current user's phone number, so saving them as a contact can
 *   share it (TDLib's `need_phone_number_privacy_exception`); false where that does not apply.
 */
data class EditableContact(
    val user: User,
    val canSharePhoneNumber: Boolean,
) {
    override fun toString() = "EditableContact(user=$user, canSharePhoneNumber=$canSharePhoneNumber)"
}
