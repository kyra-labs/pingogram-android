package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatStatistics
import dev.kyralabs.pingogram.core.domain.model.StatisticGraph

/**
 * The statistics of a supergroup or channel (`REQUIREMENTS.md` FR-P3), which Telegram gives its administrators where
 * the chat is large enough (`GroupDetails.canGetStatistics`). No cache and no updates: they are asked for when shown.
 */
interface ChatStatisticsRepository {
    /** The statistics of chat [chatId]; some graphs only as a token for [graph]. */
    suspend fun statistics(chatId: Long): Outcome<ChatStatistics>

    /** The graph of chat [chatId] that [token] stands for. */
    suspend fun graph(
        chatId: Long,
        token: String,
    ): Outcome<StatisticGraph>
}
