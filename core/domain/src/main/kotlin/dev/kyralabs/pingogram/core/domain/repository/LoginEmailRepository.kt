package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.EmailCodeInfo

/**
 * The account's login email (`REQUIREMENTS.md` FR-S1, Privacy and security): the address Telegram may send login codes
 * to instead of an SMS, shown only as Telegram's pattern of it ([address]). A new address takes effect once the code
 * Telegram sends to it is right ([setAddress], [resendCode], [checkCode]).
 *
 * Requests fail with `DomainError.EmailInvalid`, `EmailNotAllowed`, `CodeInvalid`, `CodeExpired`, `FloodWait` or
 * `Network`.
 */
interface LoginEmailRepository {
    /** Telegram's pattern of the login email ("a••••@g••••.com"), or null when the account has none. */
    suspend fun address(): Outcome<String?>

    /** Asks Telegram to send a code to [address], the new login email. */
    suspend fun setAddress(address: String): Outcome<EmailCodeInfo>

    /** Asks for the code again. */
    suspend fun resendCode(): Outcome<EmailCodeInfo>

    /** Checks [code]; once it is right, the new address is the login email. */
    suspend fun checkCode(code: String): Outcome<Unit>
}
