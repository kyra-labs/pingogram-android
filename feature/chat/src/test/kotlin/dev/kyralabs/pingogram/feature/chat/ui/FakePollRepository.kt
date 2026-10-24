package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PollVoters
import dev.kyralabs.pingogram.core.domain.repository.PollRepository

/** Polls: answers votes, stops and voters as tests set, and records every request. */
class FakePollRepository : PollRepository {
    var onVote: suspend (List<Int>) -> Outcome<Unit> = { Outcome.Success(Unit) }
    var onStop: suspend () -> Outcome<Unit> = { Outcome.Success(Unit) }
    var onVoters: suspend (optionIndex: Int, offset: Int) -> Outcome<PollVoters> = { _, _ ->
        Outcome.Success(PollVoters(totalCount = 0))
    }

    /** The message and answers of every [vote]. */
    val votes = mutableListOf<Pair<Long, List<Int>>>()

    /** The message of every [stop]. */
    val stopped = mutableListOf<Long>()

    /** The answer, offset and limit of every [voters] request. */
    val voterRequests = mutableListOf<Triple<Int, Int, Int>>()

    override suspend fun vote(
        chatId: Long,
        messageId: Long,
        optionIndexes: List<Int>,
    ): Outcome<Unit> {
        votes += messageId to optionIndexes
        return onVote(optionIndexes)
    }

    override suspend fun stop(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit> {
        stopped += messageId
        return onStop()
    }

    override suspend fun voters(
        chatId: Long,
        messageId: Long,
        optionIndex: Int,
        offset: Int,
        limit: Int,
    ): Outcome<PollVoters> {
        voterRequests += Triple(optionIndex, offset, limit)
        return onVoters(optionIndex, offset)
    }
}
