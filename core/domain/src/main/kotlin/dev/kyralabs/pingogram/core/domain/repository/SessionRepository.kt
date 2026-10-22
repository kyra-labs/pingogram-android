package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ActiveSession
import dev.kyralabs.pingogram.core.domain.model.ActiveSessions

/**
 * The sessions of the account (`REQUIREMENTS.md` FR-S1, Privacy and security): the apps and devices logged in to it.
 * Nothing is cached: each call asks Telegram through TDLib. A request that gets no answer in time fails with
 * `DomainError.Network`.
 *
 * Telegram refuses to terminate other sessions from a session that logged in recently; TDLib reports that with error
 * 406, `DomainError.Silent`, whose text must not be shown.
 */
interface SessionRepository {
    /** Every session of the account, the current one first. */
    suspend fun activeSessions(): Outcome<ActiveSessions>

    /** Logs session [sessionId] out of the account. */
    suspend fun terminate(sessionId: Long): Outcome<Unit>

    /** Logs every session but the current one out of the account. */
    suspend fun terminateAllOthers(): Outcome<Unit>

    /** Confirms the unconfirmed session [sessionId] as a login of the user's. */
    suspend fun confirm(sessionId: Long): Outcome<Unit>

    /**
     * Logs another device in to the account with the QR code it shows ([link], `tg://login?token=…`,
     * `confirmQrCodeAuthentication`), and returns its new session. Only on the user's explicit confirmation: whoever
     * shows the code gets the account. A code that expired, was used, or is no login code fails with
     * `DomainError.QrLoginExpired`.
     */
    suspend fun confirmQrCodeLogin(link: String): Outcome<ActiveSession>
}
