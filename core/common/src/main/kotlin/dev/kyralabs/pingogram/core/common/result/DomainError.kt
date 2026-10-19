package dev.kyralabs.pingogram.core.common.result

/**
 * Every failure a repository can report (`docs/ARCHITECTURE.md`, Error handling). A closed set, so ViewModels and
 * `core:ui`'s `errorText` handle each case explicitly. `core:data` maps TDLib errors onto it; only [Unknown] keeps
 * TDLib's own text, which the UI shows in debug builds only.
 */
sealed interface DomainError {
    /** Telegram asks to wait [seconds] before trying again (`FLOOD_WAIT_x`, `REQUIREMENTS.md` §6.5). */
    data class FloodWait(
        val seconds: Long,
    ) : DomainError

    /** No answer in time: no network, or Telegram is unreachable. */
    data object Network : DomainError

    /** The session is not (or no longer) authorized for the request. */
    data object Unauthorized : DomainError

    data object NotFound : DomainError

    /**
     * TDLib error 406: the request failed, and td_api.tl requires that its message is neither processed nor shown.
     * Features decide what a failed request means for them; the login flow shows [InputRejected] instead.
     */
    data object Silent : DomainError

    /**
     * Telegram rejected what was entered without a reason that may be shown (a 406 during login, e.g. for a phone
     * number of the wrong length): ask the user to check the input.
     */
    data object InputRejected : DomainError

    /** An earlier, identical request is still being processed (`TASK_ALREADY_EXISTS`). */
    data object AlreadyRequested : DomainError

    /** Telegram requires a newer app to log in (`UPDATE_APP_TO_LOGIN`). */
    data object AppUpdateRequired : DomainError

    data class Unknown(
        val code: Int,
        val message: String,
    ) : DomainError

    // Authentication input (REQUIREMENTS.md §5.1); shown next to the field they concern.

    data object PhoneNumberInvalid : DomainError

    data object PhoneNumberBanned : DomainError

    /** Too many login attempts for this phone number; Telegram gives no waiting time (`PHONE_NUMBER_FLOOD`). */
    data object PhoneNumberFlood : DomainError

    /** A login, email or password recovery code is wrong or empty. */
    data object CodeInvalid : DomainError

    data object CodeExpired : DomainError

    data object PasswordInvalid : DomainError

    data object FirstNameInvalid : DomainError

    data object LastNameInvalid : DomainError

    data object EmailInvalid : DomainError

    /** The email address cannot be used for login, e.g. a disposable address (`EMAIL_NOT_ALLOWED`). */
    data object EmailNotAllowed : DomainError

    /** The account has no recovery email address, so the password cannot be recovered (`PASSWORD_RECOVERY_NA`). */
    data object PasswordRecoveryUnavailable : DomainError

    data object PasswordRecoveryExpired : DomainError

    // Chat list actions (REQUIREMENTS.md FR-L4).

    /**
     * The chat list already has as many pinned chats as allowed (TDLib's options `pinned_chat_count_max` and
     * `pinned_archived_chat_count_max`, or the folder's limit; Telegram Premium raises them): unpin one first.
     */
    data object TooManyPinnedChats : DomainError

    // Settings (REQUIREMENTS.md FR-S1).

    /**
     * Only Telegram Premium subscribers may do this (`PREMIUM_ACCOUNT_REQUIRED`), e.g. limit who can send them voice
     * and video messages. Pingogram offers no Premium purchase (`REQUIREMENTS.md` §1.2), so the UI says so, and no
     * more.
     */
    data object PremiumRequired : DomainError
}
