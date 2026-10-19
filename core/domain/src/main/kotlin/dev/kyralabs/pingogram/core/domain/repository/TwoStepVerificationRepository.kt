package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.TwoStepVerificationState

/**
 * 2-step verification of the account (`REQUIREMENTS.md` FR-S1, Privacy and security), read-only for now: setting,
 * changing or removing the password comes later. A request that gets no answer in time fails with
 * `DomainError.Network`.
 */
interface TwoStepVerificationRepository {
    /** Whether a password is set, with its recovery email, as Telegram reports it now. */
    suspend fun state(): Outcome<TwoStepVerificationState>
}
