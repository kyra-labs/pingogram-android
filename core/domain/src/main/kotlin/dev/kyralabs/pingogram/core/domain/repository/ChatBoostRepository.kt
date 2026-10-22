package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatBoostStatus

/**
 * The boosts of channels and supergroups, read only (`REQUIREMENTS.md` FR-P5): Pingogram never boosts a chat, as that
 * needs Telegram Premium. No cache: each call asks Telegram through TDLib. A request that gets no answer in time fails
 * with `DomainError.Network`; a chat that is no supergroup or channel with `DomainError.NotFound`.
 */
interface ChatBoostRepository {
    /** Chat [chatId]'s boost level and boosts, with whether the user boosts it and has boosts left unused. */
    suspend fun status(chatId: Long): Outcome<ChatBoostStatus>
}
