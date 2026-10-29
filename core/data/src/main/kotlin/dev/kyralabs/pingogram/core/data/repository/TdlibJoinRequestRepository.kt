package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.JoinRequest
import dev.kyralabs.pingogram.core.domain.model.JoinRequestsPage
import dev.kyralabs.pingogram.core.domain.repository.JoinRequestRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [JoinRequestRepository] on TDLib's `getChatJoinRequests` (every link's requests: an empty link),
 * `processChatJoinRequest` and `processChatJoinRequests`. Nothing is cached; TDLib announces the count with
 * `updateChatPendingJoinRequests`.
 */
@AccountScoped
class TdlibJoinRequestRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : JoinRequestRepository {
        override suspend fun requests(
            chatId: Long,
            query: String,
            after: JoinRequest?,
            limit: Int,
        ): Outcome<JoinRequestsPage> {
            val offset = after?.let { TdApi.ChatJoinRequest(it.userId, it.date.toInt(), it.bio) }
            return client.request(TdApi.GetChatJoinRequests(chatId, "", query, offset, limit)).map { found ->
                JoinRequestsPage(
                    requests =
                        found.requests
                            .orEmpty()
                            .map { JoinRequest(it.userId, it.date.toLong(), it.bio.orEmpty()) }
                            .toImmutableList(),
                    totalCount = found.totalCount.coerceAtLeast(0),
                )
            }
        }

        override suspend fun process(
            chatId: Long,
            userId: Long,
            approve: Boolean,
        ): Outcome<Unit> = client.request(TdApi.ProcessChatJoinRequest(chatId, userId, approve)).map {}

        override suspend fun processAll(
            chatId: Long,
            approve: Boolean,
        ): Outcome<Unit> = client.request(TdApi.ProcessChatJoinRequests(chatId, "", approve)).map {}
    }
