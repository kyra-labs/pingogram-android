package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.LoginCodeInfo

/**
 * Changing the account's phone number (`REQUIREMENTS.md` FR-S1, Account): Telegram sends a code to the new number
 * ([sendCode], again by the next way it offers with [resendCode]), and the number changes once the code is right
 * ([checkCode]). The account, its chats and contacts stay; TDLib then announces the new number with `updateUser`.
 *
 * Every request waits as long as authentication requests, as TDLib retries them through flood waits first. They fail
 * with `DomainError.PhoneNumberInvalid`, `PhoneNumberOccupied` (another account has the number), `PhoneNumberBanned`,
 * `PhoneNumberFlood`, `CodeInvalid`, `CodeExpired`, `FloodWait` or `Network`.
 */
interface PhoneNumberChangeRepository {
    /** Asks Telegram to send a code to [phoneNumber], the new number with `+` and its calling code. */
    suspend fun sendCode(phoneNumber: String): Outcome<LoginCodeInfo>

    /** Asks for the code again, the next way Telegram offers ([LoginCodeInfo.nextType]). */
    suspend fun resendCode(): Outcome<LoginCodeInfo>

    /** Checks [code]; once it is right, the account's number is the new one. */
    suspend fun checkCode(code: String): Outcome<Unit>
}
