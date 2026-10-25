package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toMessageSender
import dev.kyralabs.pingogram.core.data.mapper.toReactionType
import dev.kyralabs.pingogram.core.data.mapper.toTdReactionType
import dev.kyralabs.pingogram.core.domain.model.AddedReaction
import dev.kyralabs.pingogram.core.domain.model.AddedReactionsPage
import dev.kyralabs.pingogram.core.domain.model.MessageViewer
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.repository.MessageInteractionRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MessageInteractionRepository] on TDLib's `getMessageViewers` and `getMessageAddedReactions`, one request per call,
 * nothing kept. TDLib announces the users and chats of an answer (`updateUser`, `updateNewChat`) before it answers.
 * Reactions of a kind added by a newer TDLib, and senders of a kind it added, are left out of a page.
 */
@Singleton
class TdlibMessageInteractionRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : MessageInteractionRepository {
        override suspend fun viewers(
            chatId: Long,
            messageId: Long,
        ): Outcome<ImmutableList<MessageViewer>> =
            client.request(TdApi.GetMessageViewers(chatId, messageId)).map { answer ->
                answer.viewers
                    .map { MessageViewer(it.userId, it.viewDate.toLong()) }
                    .sortedByDescending { it.viewDate }
                    .toImmutableList()
            }

        override suspend fun addedReactions(
            chatId: Long,
            messageId: Long,
            type: ReactionType?,
            offset: String,
            limit: Int,
        ): Outcome<AddedReactionsPage> {
            // td_api.tl: a null type returns every reaction; the paid one has its own type, not toTdReactionType's.
            val tdType = if (type == ReactionType.Paid) TdApi.ReactionTypePaid() else type?.toTdReactionType()
            val request =
                TdApi.GetMessageAddedReactions(
                    chatId,
                    messageId,
                    tdType,
                    offset,
                    limit.coerceIn(1, MAX_LIMIT),
                )
            return client.request(request).map { answer ->
                AddedReactionsPage(
                    totalCount = answer.totalCount.coerceAtLeast(0),
                    reactions =
                        answer.reactions
                            .mapNotNull { added ->
                                val reaction = added.type.toReactionType() ?: return@mapNotNull null
                                val sender = added.senderId.toMessageSender() ?: return@mapNotNull null
                                AddedReaction(reaction, sender, added.isOutgoing, added.date.toLong())
                            }.toImmutableList(),
                    nextOffset = answer.nextOffset.orEmpty(),
                )
            }
        }

        private companion object {
            /** The most reactions TDLib gives in one page (td_api.tl `getMessageAddedReactions.limit`). */
            const val MAX_LIMIT = 100
        }
    }
