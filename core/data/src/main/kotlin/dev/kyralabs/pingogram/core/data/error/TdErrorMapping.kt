package dev.kyralabs.pingogram.core.data.error

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult

/**
 * The [DomainError] for a TDLib error (`docs/ARCHITECTURE.md`, Error handling). Telegram's error names
 * (`PHONE_CODE_INVALID`) are matched as the first word of the message; TDLib's own texts for the same cases
 * ("First name must be non-empty") are matched too.
 */
internal fun TdError.toDomainError(): DomainError {
    val floodWait = retryAfter
    return when {
        floodWait != null -> DomainError.FloodWait(floodWait.inWholeSeconds)

        // td_api.tl: the message of a 406 error must not be processed in any way.
        code == SILENT -> DomainError.Silent

        else -> ERROR_NAME.find(message)?.value?.let(TELEGRAM_ERRORS::get) ?: TDLIB_MESSAGES[message] ?: byCode()
    }
}

private fun TdError.byCode(): DomainError =
    when {
        code == UNAUTHORIZED -> DomainError.Unauthorized
        code == NOT_FOUND -> DomainError.NotFound
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

/** An upper-case Telegram error name at the start of the message, e.g. `PHONE_NUMBER_INVALID`. */
private val ERROR_NAME = Regex("""^[A-Z][A-Z0-9_]*(?![a-z])""")

private val NETWORK = Regex("""network|connection|timeout""", RegexOption.IGNORE_CASE)

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
        "PASSWORD_HASH_INVALID" to DomainError.PasswordInvalid,
        "FIRSTNAME_INVALID" to DomainError.FirstNameInvalid,
        "LASTNAME_INVALID" to DomainError.LastNameInvalid,
        "EMAIL_INVALID" to DomainError.EmailInvalid,
        "EMAIL_NOT_ALLOWED" to DomainError.EmailNotAllowed,
        "PASSWORD_RECOVERY_NA" to DomainError.PasswordRecoveryUnavailable,
        "PASSWORD_RECOVERY_EXPIRED" to DomainError.PasswordRecoveryExpired,
        "TASK_ALREADY_EXISTS" to DomainError.AlreadyRequested,
        "UPDATE_APP_TO_LOGIN" to DomainError.AppUpdateRequired,
    )

/** TDLib's own validation messages (td/telegram/AuthManager.cpp) for errors Telegram also reports by name. */
private val TDLIB_MESSAGES: Map<String, DomainError> =
    mapOf(
        "Phone number must be non-empty" to DomainError.PhoneNumberInvalid,
        "Code must be non-empty" to DomainError.CodeInvalid,
        "Invalid recovery code" to DomainError.CodeInvalid,
        "First name must be non-empty" to DomainError.FirstNameInvalid,
        "Email address must be non-empty" to DomainError.EmailInvalid,
        // The 2-step verification password uses an algorithm this TDLib does not know.
        "Application update is needed to log in" to DomainError.AppUpdateRequired,
    )
