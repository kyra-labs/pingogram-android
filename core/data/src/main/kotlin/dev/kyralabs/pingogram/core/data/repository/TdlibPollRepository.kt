package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toPollVoters
import dev.kyralabs.pingogram.core.domain.model.PollVoters
import dev.kyralabs.pingogram.core.domain.repository.PollRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PollRepository] on `setPollAnswer`, `stopPoll` and `getPollVoters`, without a cache: TDLib marks a vote on its way
 * at once (`pollOption.is_being_chosen`) and announces every change of the poll with `updatePoll`, which
 * [TdlibMessageRepository]'s windows apply to each message showing it.
 */
@Singleton
class TdlibPollRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : PollRepository {
        override suspend fun vote(
            chatId: Long,
            messageId: Long,
            optionIndexes: List<Int>,
        ): Outcome<Unit> = client.request(TdApi.SetPollAnswer(chatId, messageId, optionIndexes.toIntArray())).map {}

        // No reply markup: stopping a poll leaves its message's buttons as they are, which only bots set.
        override suspend fun stop(
            chatId: Long,
            messageId: Long,
        ): Outcome<Unit> = client.request(TdApi.StopPoll(chatId, messageId, null)).map {}

        override suspend fun voters(
            chatId: Long,
            messageId: Long,
            optionIndex: Int,
            offset: Int,
            limit: Int,
        ): Outcome<PollVoters> =
            client.request(TdApi.GetPollVoters(chatId, messageId, optionIndex, offset, limit)).map { it.toPollVoters() }
    }
