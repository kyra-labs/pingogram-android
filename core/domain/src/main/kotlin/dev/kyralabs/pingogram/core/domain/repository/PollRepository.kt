package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PollVoters

/**
 * Voting in the polls of messages (`REQUIREMENTS.md` FR-C4), stopping them and listing their voters. Each call is one
 * tap of the user's, never made on its own (Telegram's API terms). Nothing is kept here: TDLib announces every change
 * of a poll (the user's vote on its way, the counts, a stop) with `updatePoll`, which `MessageRepository.history`
 * applies to every loaded message that shows the poll.
 *
 * Every call fails with a `DomainError`: `FloodWait`, `Network` for a request that gets no answer in time,
 * `PollClosed` for a poll that was closed meanwhile, `NotFound` for a message TDLib does not know, or another one when
 * TDLib refuses it.
 */
interface PollRepository {
    /**
     * Votes for [optionIndexes] (0-based, TDLib's order of the options, `PollOption.index`) in the poll of message
     * [messageId] of chat [chatId]; an empty list takes the user's vote back. Several answers only where the poll
     * allows them. Succeeds once Telegram took the vote.
     */
    suspend fun vote(
        chatId: Long,
        messageId: Long,
        optionIndexes: List<Int>,
    ): Outcome<Unit>

    /** Closes the poll of message [messageId] of chat [chatId] for everyone, which only its author may do. */
    suspend fun stop(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit>

    /**
     * Up to [limit] of those who voted for answer [optionIndex] of the poll of message [messageId] of chat [chatId],
     * after the first [offset]; the users and chats among them are announced to `UserRepository` and `ChatRepository`
     * before this returns.
     */
    suspend fun voters(
        chatId: Long,
        messageId: Long,
        optionIndex: Int,
        offset: Int,
        limit: Int,
    ): Outcome<PollVoters>
}
