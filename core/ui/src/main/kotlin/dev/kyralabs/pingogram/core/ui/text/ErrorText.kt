package dev.kyralabs.pingogram.core.ui.text

import android.content.pm.ApplicationInfo
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.R

/**
 * The text shown for [error] (`docs/ARCHITECTURE.md`, Error handling). TDLib's own text of an unknown error appears in
 * debug builds only. [DomainError.Silent] has no text: show nothing for it.
 */
@Composable
@ReadOnlyComposable
fun errorText(error: DomainError): String = errorMessage(LocalResources.current, error, showDetails = isDebuggable())

/** Whether this is a debuggable (debug) build, which may show TDLib's own error texts. */
@Composable
@ReadOnlyComposable
fun isDebuggable(): Boolean = LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

/** [errorText] outside composition, e.g. for a snackbar shown from an effect. */
fun errorMessage(
    resources: Resources,
    error: DomainError,
    showDetails: Boolean,
): String =
    when (error) {
        is DomainError.FloodWait -> {
            resources.getString(R.string.error_flood_wait, durationText(resources, error.seconds))
        }

        is DomainError.Unknown -> {
            if (showDetails) {
                resources.getString(R.string.error_unknown_details, error.code, error.message)
            } else {
                resources.getString(R.string.error_unknown)
            }
        }

        DomainError.Silent -> {
            ""
        }

        else -> {
            resources.getString(error.messageRes())
        }
    }

private fun DomainError.messageRes(): Int =
    when (this) {
        DomainError.Network -> R.string.error_network
        DomainError.Unauthorized -> R.string.error_unauthorized
        DomainError.NotFound -> R.string.error_not_found
        DomainError.AlreadyRequested -> R.string.error_already_requested
        DomainError.AppUpdateRequired -> R.string.error_app_update_required
        DomainError.InputRejected -> R.string.error_input_rejected
        DomainError.PhoneNumberInvalid -> R.string.error_phone_number_invalid
        DomainError.PhoneNumberBanned -> R.string.error_phone_number_banned
        DomainError.PhoneNumberFlood -> R.string.error_phone_number_flood
        DomainError.PhoneNumberOccupied -> R.string.error_phone_number_occupied
        DomainError.BirthdateInvalid -> R.string.error_birthdate_invalid
        DomainError.CodeInvalid -> R.string.error_code_invalid
        DomainError.CodeExpired -> R.string.error_code_expired
        DomainError.PasswordInvalid -> R.string.error_password_invalid
        DomainError.FirstNameInvalid -> R.string.error_first_name_invalid
        DomainError.LastNameInvalid -> R.string.error_last_name_invalid
        DomainError.EmailInvalid -> R.string.error_email_invalid
        DomainError.EmailNotAllowed -> R.string.error_email_not_allowed
        DomainError.PasswordRecoveryUnavailable -> R.string.error_password_recovery_unavailable
        DomainError.PasswordRecoveryExpired -> R.string.error_password_recovery_expired
        DomainError.TooManyPinnedChats -> R.string.error_too_many_pinned_chats
        DomainError.TooManyChatFolders -> R.string.error_too_many_chat_folders
        DomainError.ChatFolderEmpty -> R.string.error_chat_folder_empty
        DomainError.FileUnavailable -> R.string.error_file_unavailable
        DomainError.FileTooLarge -> R.string.error_file_too_large
        DomainError.SendingNotAllowed -> R.string.error_sending_not_allowed
        DomainError.PremiumRequired -> R.string.error_premium_required
        DomainError.UsernameTaken -> R.string.error_username_taken
        DomainError.UsernameInvalid -> R.string.error_username_invalid
        DomainError.UsernameForSale -> R.string.error_username_for_sale
        DomainError.BioTooLong -> R.string.error_bio_too_long
        DomainError.PhotoInvalid -> R.string.error_photo_invalid
        DomainError.TitleInvalid -> R.string.error_title_invalid
        DomainError.TooManyChats -> R.string.error_too_many_chats
        DomainError.TooManyPublicLinks -> R.string.error_too_many_public_links
        DomainError.AccountRestricted -> R.string.error_account_restricted
        DomainError.AdminRightsRequired -> R.string.error_admin_rights_required
        DomainError.DescriptionTooLong -> R.string.error_description_too_long
        DomainError.NotMutualContact -> R.string.error_not_mutual_contact
        DomainError.TooManyAdmins -> R.string.error_too_many_admins
        DomainError.InviteLinkInvalid -> R.string.error_invite_link_invalid
        DomainError.ChatFull -> R.string.error_chat_full
        DomainError.QrLoginExpired -> R.string.error_qr_login_expired
        DomainError.CallPrivacyRestricted -> R.string.error_call_privacy_restricted
        DomainError.CallPeerOutdated -> R.string.error_call_peer_outdated
        is DomainError.FloodWait, is DomainError.Unknown, DomainError.Silent -> R.string.error_unknown
    }
