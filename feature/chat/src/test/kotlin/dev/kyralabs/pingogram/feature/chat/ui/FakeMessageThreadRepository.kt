package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageThread
import dev.kyralabs.pingogram.core.domain.repository.MessageThreadRepository

/**
 * The threads of messages, by chat and message id ([threads]; none found otherwise, [DomainError.NotFound]), what
 * [thread] answers for those it does not hold ([failure]), and every request.
 */
class FakeMessageThreadRepository : MessageThreadRepository {
    val threads = mutableMapOf<Pair<Long, Long>, MessageThread>()

    var failure: DomainError = DomainError.NotFound

    /** The chat and message of every [thread] call. */
    val requests = mutableListOf<Pair<Long, Long>>()

    override suspend fun thread(
        chatId: Long,
        messageId: Long,
    ): Outcome<MessageThread> {
        requests += chatId to messageId
        return threads[chatId to messageId]?.let { Outcome.Success(it) } ?: Outcome.Failure(failure)
    }
}
