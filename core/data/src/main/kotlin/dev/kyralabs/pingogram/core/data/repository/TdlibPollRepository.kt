package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toInputPollOption
import dev.kyralabs.pingogram.core.data.mapper.toPollVoters
import dev.kyralabs.pingogram.core.domain.model.NewPollOption
import dev.kyralabs.pingogram.core.domain.model.PollLimits
import dev.kyralabs.pingogram.core.domain.model.PollVoters
import dev.kyralabs.pingogram.core.domain.repository.PollRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [PollRepository] on `setPollAnswer`, `stopPoll`, `getPollVoters` and `addPollOption`, without a cache: TDLib marks a
 * vote on its way at once (`pollOption.is_being_chosen`) and announces every change of the poll with `updatePoll`,
 * which [TdlibMessageRepository]'s windows apply to each message showing it.
 */
@AccountScoped
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

        override suspend fun addOption(
            chatId: Long,
            messageId: Long,
            option: NewPollOption,
        ): Outcome<Unit> = client.request(TdApi.AddPollOption(chatId, messageId, option.toInputPollOption())).map {}

        // Read when a poll form opens, not followed: TDLib changes these options only after Telegram does.
        override suspend fun limits(): PollLimits =
            PollLimits(
                maxOptions = client.intOption(ANSWER_COUNT_MAX) ?: PollLimits.DEFAULT_MAX_OPTIONS,
                maxOpenPeriod = client.intOption(OPEN_PERIOD_MAX) ?: 0,
            )

        private suspend fun TdlibClient.intOption(name: String): Int? =
            ((request(TdApi.GetOption(name)) as? Outcome.Success)?.value as? TdApi.OptionValueInteger)
                ?.value
                ?.toInt()
                ?.takeIf { it > 0 }

        private companion object {
            const val ANSWER_COUNT_MAX = "poll_answer_count_max"
            const val OPEN_PERIOD_MAX = "poll_open_period_max"
        }
    }
