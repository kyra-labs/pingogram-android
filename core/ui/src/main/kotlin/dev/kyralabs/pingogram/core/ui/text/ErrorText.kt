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
        DomainError.PhoneNumberInvalid -> R.string.error_phone_number_invalid
        DomainError.PhoneNumberBanned -> R.string.error_phone_number_banned
        DomainError.PhoneNumberFlood -> R.string.error_phone_number_flood
        DomainError.CodeInvalid -> R.string.error_code_invalid
        DomainError.CodeExpired -> R.string.error_code_expired
        DomainError.PasswordInvalid -> R.string.error_password_invalid
        DomainError.FirstNameInvalid -> R.string.error_first_name_invalid
        DomainError.LastNameInvalid -> R.string.error_last_name_invalid
        DomainError.EmailInvalid -> R.string.error_email_invalid
        DomainError.EmailNotAllowed -> R.string.error_email_not_allowed
        DomainError.PasswordRecoveryUnavailable -> R.string.error_password_recovery_unavailable
        DomainError.PasswordRecoveryExpired -> R.string.error_password_recovery_expired
        is DomainError.FloodWait, is DomainError.Unknown, DomainError.Silent -> R.string.error_unknown
    }
