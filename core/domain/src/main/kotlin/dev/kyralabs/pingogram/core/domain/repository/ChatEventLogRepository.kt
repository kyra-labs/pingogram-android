package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatEvent
import dev.kyralabs.pingogram.core.domain.model.ChatEventFilter
import kotlinx.collections.immutable.ImmutableList

/**
 * The recent actions of a supergroup or channel (`REQUIREMENTS.md` FR-P3), which only its administrators may read:
 * Telegram keeps those of the last 48 hours. No cache and no updates: each page is asked for when shown.
 */
interface ChatEventLogRepository {
    /**
     * Up to [limit] actions of chat [chatId] older than action [fromEventId] (0: the newest), newest first, of the
     * kinds in [filters] (empty: every kind). Fewer than [limit] means there are no older ones.
     */
    suspend fun events(
        chatId: Long,
        fromEventId: Long,
        filters: Set<ChatEventFilter>,
        limit: Int = PAGE_SIZE,
    ): Outcome<ImmutableList<ChatEvent>>

    companion object {
        const val PAGE_SIZE = 50
    }
}
