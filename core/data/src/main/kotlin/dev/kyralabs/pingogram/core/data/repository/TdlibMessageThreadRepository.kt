package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.domain.model.MessageThread
import dev.kyralabs.pingogram.core.domain.repository.MessageThreadRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [MessageThreadRepository] on TDLib's `getMessageThread`, without a cache: the conversation of a thread asks once as
 * it opens, and its replies come through `MessageRepository`. TDLib answers with the thread's chat (the discussion
 * group of a channel post), its id, the messages that start it (newest first, which the domain turns around) and the
 * read state of its replies.
 */
@AccountScoped
class TdlibMessageThreadRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : MessageThreadRepository {
        override suspend fun thread(
            chatId: Long,
            messageId: Long,
        ): Outcome<MessageThread> =
            client.request(TdApi.GetMessageThread(chatId, messageId)).map { info ->
                MessageThread(
                    chatId = info.chatId,
                    threadId = info.messageThreadId,
                    head =
                        info.messages
                            .filterNotNull()
                            .map { it.toMessage() }
                            .sortedBy { it.id }
                            .toImmutableList(),
                    replyCount = info.replyInfo?.replyCount ?: 0,
                    unreadCount = info.unreadMessageCount,
                    lastReadInboxMessageId = info.replyInfo?.lastReadInboxMessageId ?: 0,
                    lastReadOutboxMessageId = info.replyInfo?.lastReadOutboxMessageId ?: 0,
                )
            }
    }
