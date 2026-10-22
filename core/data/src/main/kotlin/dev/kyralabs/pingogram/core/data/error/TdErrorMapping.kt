package dev.kyralabs.pingogram.core.data.error

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import kotlin.time.Duration.Companion.seconds

/**
 * The [DomainError] for a TDLib error (`docs/ARCHITECTURE.md`, Error handling). Telegram's error names
 * (`PHONE_CODE_INVALID`) are matched as the first word of the message; TDLib's own texts for the same cases
 * ("First name must be non-empty") are matched too.
 */
internal fun TdError.toDomainError(): DomainError {
    val floodWait =
        retryAfter ?: TOO_FRESH
            .find(message)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
            ?.seconds
    return when {
        floodWait != null -> {
            DomainError.FloodWait(floodWait.inWholeSeconds)
        }

        // td_api.tl: the message of a 406 error must not be processed in any way.
        code == SILENT -> {
            DomainError.Silent
        }

        else -> {
            ERROR_NAME.find(message)?.value?.let(TELEGRAM_ERRORS::get)
                ?: TDLIB_MESSAGES[message]
                ?: TDLIB_PATTERNS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(message) }?.second
                ?: byCode()
        }
    }
}

private fun TdError.byCode(): DomainError =
    when {
        code == UNAUTHORIZED -> DomainError.Unauthorized
        code == NOT_FOUND || TDLIB_NOT_FOUND.containsMatchIn(message) -> DomainError.NotFound
        NETWORK.containsMatchIn(message) -> DomainError.Network
        else -> DomainError.Unknown(code, message)
    }

internal fun <T> TdResult<T>.toOutcome(): Outcome<T> =
    when (this) {
        is TdResult.Success -> Outcome.Success(value)
        is TdResult.Failure -> Outcome.Failure(error.toDomainError())
    }

private const val UNAUTHORIZED = 401
private const val NOT_FOUND = 404
private const val SILENT = 406

/**
 * Telegram's refusals to change 2-step verification for a while, with the seconds left: `PASSWORD_TOO_FRESH_x` (the
 * password was set only recently) and `SESSION_TOO_FRESH_x` (this session logged in only recently). The user can only
 * wait, as for `FLOOD_WAIT_x`.
 */
private val TOO_FRESH = Regex("""^(?:PASSWORD|SESSION)_TOO_FRESH_(\d+)""")

/** An upper-case Telegram error name at the start of the message, e.g. `PHONE_NUMBER_INVALID`. */
private val ERROR_NAME = Regex("""^[A-Z][A-Z0-9_]*(?![a-z])""")

private val NETWORK = Regex("""network|connection|timeout""", RegexOption.IGNORE_CASE)

/** TDLib's own 400 texts for unknown ids (td/telegram), e.g. "User not found", "Chat info not found". */
private val TDLIB_NOT_FOUND = Regex("""^[A-Z][a-z][a-z ]* not found\b""")

private val TELEGRAM_ERRORS: Map<String, DomainError> =
    mapOf(
        "PHONE_NUMBER_INVALID" to DomainError.PhoneNumberInvalid,
        "PHONE_NUMBER_EMPTY" to DomainError.PhoneNumberInvalid,
        "PHONE_NUMBER_BANNED" to DomainError.PhoneNumberBanned,
        "PHONE_NUMBER_FLOOD" to DomainError.PhoneNumberFlood,
        "PHONE_CODE_INVALID" to DomainError.CodeInvalid,
        "PHONE_CODE_EMPTY" to DomainError.CodeInvalid,
        "CODE_INVALID" to DomainError.CodeInvalid,
        "EMAIL_CODE_INVALID" to DomainError.CodeInvalid,
        "PHONE_CODE_EXPIRED" to DomainError.CodeExpired,
        "CODE_EXPIRED" to DomainError.CodeExpired,
        "EMAIL_CODE_EXPIRED" to DomainError.CodeExpired,
        // account.verifyEmail, which checks the email code of an address entered during login.
        "EMAIL_VERIFY_EXPIRED" to DomainError.CodeExpired,
        // account.confirmPasswordEmail: the recovery email's confirmation code is too old.
        "EMAIL_HASH_EXPIRED" to DomainError.CodeExpired,
        "PASSWORD_HASH_INVALID" to DomainError.PasswordInvalid,
        "FIRSTNAME_INVALID" to DomainError.FirstNameInvalid,
        "LASTNAME_INVALID" to DomainError.LastNameInvalid,
        "EMAIL_INVALID" to DomainError.EmailInvalid,
        "EMAIL_NOT_ALLOWED" to DomainError.EmailNotAllowed,
        "PASSWORD_RECOVERY_NA" to DomainError.PasswordRecoveryUnavailable,
        "PASSWORD_RECOVERY_EXPIRED" to DomainError.PasswordRecoveryExpired,
        "TASK_ALREADY_EXISTS" to DomainError.AlreadyRequested,
        "UPDATE_APP_TO_LOGIN" to DomainError.AppUpdateRequired,
        // Requests only Telegram Premium subscribers may make, e.g. account.setPrivacy for voice messages.
        "PREMIUM_ACCOUNT_REQUIRED" to DomainError.PremiumRequired,
        // Editing the profile: account.updateUsername, account.updateProfile and photos.uploadProfilePhoto.
        "USERNAME_OCCUPIED" to DomainError.UsernameTaken,
        "USERNAME_INVALID" to DomainError.UsernameInvalid,
        "USERNAME_PURCHASE_AVAILABLE" to DomainError.UsernameForSale,
        "ABOUT_TOO_LONG" to DomainError.BioTooLong,
        // Contacts: contacts.addContact without a first name, contacts.resolveUsername for a username no one has.
        "CONTACT_NAME_EMPTY" to DomainError.FirstNameInvalid,
        "USERNAME_NOT_OCCUPIED" to DomainError.NotFound,
        "PHOTO_CROP_SIZE_SMALL" to DomainError.PhotoInvalid,
        "PHOTO_INVALID_DIMENSIONS" to DomainError.PhotoInvalid,
        "PHOTO_EXT_INVALID" to DomainError.PhotoInvalid,
        "PHOTO_INVALID" to DomainError.PhotoInvalid,
        "IMAGE_PROCESS_FAILED" to DomainError.PhotoInvalid,
        // An upload of more parts than the account may send: a file over Telegram's size limit for it.
        "FILE_PARTS_INVALID" to DomainError.FileTooLarge,
        // Rights Telegram checks itself when TDLib did not know them yet.
        "CHAT_SEND_MEDIA_FORBIDDEN" to DomainError.SendingNotAllowed,
        "CHAT_SEND_PHOTOS_FORBIDDEN" to DomainError.SendingNotAllowed,
        "CHAT_SEND_VIDEOS_FORBIDDEN" to DomainError.SendingNotAllowed,
        "CHAT_SEND_DOCS_FORBIDDEN" to DomainError.SendingNotAllowed,
        "CHAT_SEND_POLL_FORBIDDEN" to DomainError.SendingNotAllowed,
        // Creating groups and channels: messages.createChat, channels.createChannel, channels.updateUsername.
        "CHAT_TITLE_EMPTY" to DomainError.TitleInvalid,
        "CHANNELS_TOO_MUCH" to DomainError.TooManyChats,
        "CHANNELS_ADMIN_PUBLIC_TOO_MUCH" to DomainError.TooManyPublicLinks,
        "USER_RESTRICTED" to DomainError.AccountRestricted,
        // Administering groups and channels: channels.editAdmin, channels.editBanned, messages.editChatTitle, ….
        "CHAT_ADMIN_REQUIRED" to DomainError.AdminRightsRequired,
        "RIGHT_FORBIDDEN" to DomainError.AdminRightsRequired,
        "USER_ADMIN_INVALID" to DomainError.AdminRightsRequired,
        "ADMIN_RANK_INVALID" to DomainError.AdminRightsRequired,
        "USER_CREATOR" to DomainError.AdminRightsRequired,
        "CHAT_ABOUT_TOO_LONG" to DomainError.DescriptionTooLong,
        "USER_NOT_MUTUAL_CONTACT" to DomainError.NotMutualContact,
        "ADMINS_TOO_MUCH" to DomainError.TooManyAdmins,
    )

/**
 * TDLib's own messages for errors it finds before asking Telegram: login input Telegram also reports by name
 * (td/telegram/AuthManager.cpp), and limits TDLib checks itself.
 */
private val TDLIB_MESSAGES: Map<String, DomainError> =
    mapOf(
        "Phone number must be non-empty" to DomainError.PhoneNumberInvalid,
        "Code must be non-empty" to DomainError.CodeInvalid,
        "Invalid recovery code" to DomainError.CodeInvalid,
        "First name must be non-empty" to DomainError.FirstNameInvalid,
        "Email address must be non-empty" to DomainError.EmailInvalid,
        // The 2-step verification password uses an algorithm this TDLib does not know.
        "Application update is needed to log in" to DomainError.AppUpdateRequired,
        // toggleChatIsPinned in the main list or the archive (MessagesManager::toggle_dialog_is_pinned) and in a
        // folder (DialogFilter::check_limits). TDLib answers before it tells Telegram, so no server error follows.
        "The maximum number of pinned chats exceeded" to DomainError.TooManyPinnedChats,
        // setUsername checks the characters itself (UserManager::set_username, is_allowed_username).
        "Username is invalid" to DomainError.UsernameInvalid,
        // can_send_message_content (td/telegram/MessageContent.cpp), for content the chat does not take.
        "Polls can't be sent to the private chat" to DomainError.SendingNotAllowed,
        "Non-anonymous polls can't be sent to channel chats" to DomainError.SendingNotAllowed,
        "Message can't be sent to a secret chat" to DomainError.SendingNotAllowed,
        // createNewBasicGroupChat and createNewSupergroupChat with a title that is empty once cleaned
        // (ChatManager::create_new_chat, create_new_channel).
        "Title must be non-empty" to DomainError.TitleInvalid,
    )

/**
 * TDLib's own messages that carry details: the size checks of files to send (check_full_local_location,
 * td/telegram/files/FileLoaderUtils.cpp: "File of size … bytes is too big…") and the rights checks of
 * can_send_message_content ("Not enough rights to send photos to the chat").
 */
private val TDLIB_PATTERNS: List<Pair<Regex, DomainError>> =
    listOf(
        Regex("""^File of size \d+ bytes is too big""") to DomainError.FileTooLarge,
        Regex("""^Not enough rights to send """) to DomainError.SendingNotAllowed,
        // TDLib's own rights checks of the administration functions ("Not enough rights to …"), after the sending
        // check above, as the first match wins.
        Regex("""^Not enough rights\b""") to DomainError.AdminRightsRequired,
    )
