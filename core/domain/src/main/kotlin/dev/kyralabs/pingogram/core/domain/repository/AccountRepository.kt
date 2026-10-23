package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome

/**
 * The Telegram account itself (`REQUIREMENTS.md` FR-S1, Privacy and security; Google Play's account deletion policy,
 * `docs/PLAY_STORE.md`): how long it may stay unused before Telegram deletes it, and deleting it now. A request that
 * gets no answer in time fails with `DomainError.Network`; a wrong 2-step verification password with
 * `DomainError.PasswordInvalid`.
 */
interface AccountRepository {
    /** How many days without coming online Telegram waits before it deletes the account. */
    suspend fun accountTtl(): Outcome<Int>

    /** Sets how many [days] without coming online Telegram waits before it deletes the account (30 to 730). */
    suspend fun setAccountTtl(days: Int): Outcome<Unit>

    /**
     * Deletes the account on Telegram, everything in it, for good, giving [reason] (empty for none). [password] is the
     * 2-step verification password where one is set, empty otherwise. TDLib then logs out on its own: the login steps
     * go through `LoggingOut` and `Closed` as after a logout.
     */
    suspend fun deleteAccount(
        reason: String,
        password: String,
    ): Outcome<Unit>
}
