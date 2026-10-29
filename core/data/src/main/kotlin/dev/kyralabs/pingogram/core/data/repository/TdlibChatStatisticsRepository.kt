package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toChatStatistics
import dev.kyralabs.pingogram.core.data.mapper.toStatisticGraph
import dev.kyralabs.pingogram.core.domain.model.ChatStatistics
import dev.kyralabs.pingogram.core.domain.model.StatisticGraph
import dev.kyralabs.pingogram.core.domain.repository.ChatStatisticsRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [ChatStatisticsRepository] on TDLib's `getChatStatistics` and `getStatisticalGraph` (the graphs it gives only as a
 * token). Telegram's colors for light and dark (`is_dark`) are not used: the theme colors the charts.
 */
@AccountScoped
class TdlibChatStatisticsRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : ChatStatisticsRepository {
        override suspend fun statistics(chatId: Long): Outcome<ChatStatistics> =
            client.request(TdApi.GetChatStatistics(chatId, false)).map { it.toChatStatistics() }

        override suspend fun graph(
            chatId: Long,
            token: String,
        ): Outcome<StatisticGraph> =
            client.request(TdApi.GetStatisticalGraph(chatId, token, 0)).map {
                it.toStatisticGraph()
                    ?: StatisticGraph.Failed("")
            }
    }
