package dev.kyralabs.pingogram.core.domain.model

/**
 * Whether the user may transfer the ownership of a chat now (td_api.tl `canTransferOwnership`): Telegram asks for a
 * 2-step verification password that is not new, on a session that is not new.
 */
sealed interface OwnershipTransferCheck {
    data object Allowed : OwnershipTransferCheck

    /** The account has no 2-step verification password. */
    data object PasswordNeeded : OwnershipTransferCheck

    /** The password was set only recently: it may be used for this in [retryAfterSeconds]. */
    data class PasswordTooNew(
        val retryAfterSeconds: Int,
    ) : OwnershipTransferCheck

    /** This device logged in only recently: it may transfer in [retryAfterSeconds]. */
    data class SessionTooNew(
        val retryAfterSeconds: Int,
    ) : OwnershipTransferCheck
}
