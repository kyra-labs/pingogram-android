package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toChatEvent
import dev.kyralabs.pingogram.core.data.mapper.toTdFilters
import dev.kyralabs.pingogram.core.domain.model.ChatEvent
import dev.kyralabs.pingogram.core.domain.model.ChatEventFilter
import dev.kyralabs.pingogram.core.domain.repository.ChatEventLogRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/** [ChatEventLogRepository] on TDLib's `getChatEventLog`, without a search query or a list of members. */
@AccountScoped
class TdlibChatEventLogRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : ChatEventLogRepository {
        override suspend fun events(
            chatId: Long,
            fromEventId: Long,
            filters: Set<ChatEventFilter>,
            limit: Int,
        ): Outcome<ImmutableList<ChatEvent>> =
            client
                .request(TdApi.GetChatEventLog(chatId, "", fromEventId, limit, filters.toTdFilters(), null))
                .map { events -> events.events.map { it.toChatEvent(chatId) }.toImmutableList() }
    }
