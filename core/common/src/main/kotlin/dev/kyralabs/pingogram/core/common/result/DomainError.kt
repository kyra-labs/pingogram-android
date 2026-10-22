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

    // Sending files, contacts and polls (REQUIREMENTS.md FR-C5).

    /**
     * A picked file or contact could not be read: it was moved or deleted, the app that shared it no longer lets it be
     * read, or Android cannot decode it.
     */
    data object FileUnavailable : DomainError

    /** The file is larger than Telegram accepts (TDLib's own limit, or Telegram's for the account). */
    data object FileTooLarge : DomainError

    /**
     * The chat does not allow sending this kind of message (TDLib's rights checks): polls in a private chat, media for
     * a restricted member, a non-anonymous poll in a channel.
     */
    data object SendingNotAllowed : DomainError

    // Settings (REQUIREMENTS.md FR-S1).

    /**
     * Only Telegram Premium subscribers may do this (`PREMIUM_ACCOUNT_REQUIRED`), e.g. limit who can send them voice
     * and video messages. Pingogram offers no Premium purchase (`REQUIREMENTS.md` §1.2), so the UI says so, and no
     * more.
     */
    data object PremiumRequired : DomainError

    /** Someone else has the username the user wants (`USERNAME_OCCUPIED`). */
    data object UsernameTaken : DomainError

    /** Telegram does not accept the username (`USERNAME_INVALID`). */
    data object UsernameInvalid : DomainError

    /** The username is for sale on Fragment and can only be bought there (`USERNAME_PURCHASE_AVAILABLE`). */
    data object UsernameForSale : DomainError

    /** The bio is longer than Telegram accepts from the user (`ABOUT_TOO_LONG`). */
    data object BioTooLong : DomainError

    /**
     * The image cannot be a profile photo: too small, of unusable dimensions, or unreadable (`PHOTO_CROP_SIZE_SMALL`,
     * `IMAGE_PROCESS_FAILED` …, or an image the app could not read).
     */
    data object PhotoInvalid : DomainError

    // Creating groups and channels (REQUIREMENTS.md FR-P4).

    /** The title of a new group or channel is empty, or Telegram does not accept it (`CHAT_TITLE_EMPTY`). */
    data object TitleInvalid : DomainError

    /**
     * The user is in as many groups and channels as Telegram allows (`CHANNELS_TOO_MUCH`): they have to leave one
     * before they can create or join another.
     */
    data object TooManyChats : DomainError

    /**
     * The user already has as many public groups and channels as Telegram allows (`CHANNELS_ADMIN_PUBLIC_TOO_MUCH`):
     * one of them has to become private before another can have a username.
     */
    data object TooManyPublicLinks : DomainError

    /**
     * Telegram limited the account (`USER_RESTRICTED`, e.g. after reports of spam), so it cannot create groups or
     * channels for now.
     */
    data object AccountRestricted : DomainError

    // Groups and channels (REQUIREMENTS.md FR-P3).

    /**
     * The current user's rights do not allow this (`CHAT_ADMIN_REQUIRED`, `RIGHT_FORBIDDEN`, TDLib's "Not enough
     * rights"): e.g. an administrator granting a right they lack themselves, or editing one they did not promote.
     */
    data object AdminRightsRequired : DomainError

    /** The description is longer than Telegram accepts (`CHAT_ABOUT_TOO_LONG`). */
    data object DescriptionTooLong : DomainError

    /** Only a mutual contact can be made an administrator of a basic group, or added (`USER_NOT_MUTUAL_CONTACT`). */
    data object NotMutualContact : DomainError

    /** The group or channel has as many administrators as Telegram allows (`ADMINS_TOO_MUCH`). */
    data object TooManyAdmins : DomainError

    // Joining groups and channels, and logging in other devices (REQUIREMENTS.md FR-P5).

    /**
     * The invite link expired, was revoked, reached its limit of members, or is no invite link at all
     * (`INVITE_HASH_EXPIRED`, `INVITE_HASH_INVALID`, TDLib's "Wrong invite link").
     */
    data object InviteLinkInvalid : DomainError

    /** The group has as many members as Telegram allows (`USERS_TOO_MUCH`). */
    data object ChatFull : DomainError

    /**
     * The QR code of a login on another device expired, was used already, or is no login code
     * (`AUTH_TOKEN_EXPIRED`, `AUTH_TOKEN_INVALID`, `AUTH_TOKEN_ALREADY_ACCEPTED`): the other device shows a new one.
     */
    data object QrLoginExpired : DomainError
}
