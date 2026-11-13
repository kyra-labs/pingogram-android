package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.repository.SuggestedPostRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [SuggestedPostRepository] on `approveSuggestedPost` and `declineSuggestedPost`, without a cache: TDLib reports the
 * post's new state with `updateMessageSuggestedPostInfo`, which [TdlibMessageRepository]'s windows apply to its
 * message.
 */
@AccountScoped
class TdlibSuggestedPostRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : SuggestedPostRepository {
        /** `send_date` 0: the time its author asked for, or now where they asked for none. */
        override suspend fun approve(
            chatId: Long,
            messageId: Long,
        ): Outcome<Unit> = client.request(TdApi.ApproveSuggestedPost(chatId, messageId, 0)).map {}

        override suspend fun decline(
            chatId: Long,
            messageId: Long,
            comment: String,
        ): Outcome<Unit> = client.request(TdApi.DeclineSuggestedPost(chatId, messageId, comment.trim())).map {}
    }
