package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ScheduledMessage
import dev.kyralabs.pingogram.core.domain.repository.ScheduledMessageRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The scheduled messages of each chat in memory, earliest first as the repository gives them, and every call. A
 * [loadError] makes every collection fail with it; [onCall] answers sending now, rescheduling and deleting.
 */
class FakeScheduledMessageRepository : ScheduledMessageRepository {
    val scheduled = MutableStateFlow<Map<Long, List<ScheduledMessage>>>(emptyMap())

    /** Each collection fails with this, while it is not null. */
    var loadError: Outcome.Failure? = null

    /** The chats collected, in order. */
    val collected = mutableListOf<Long>()

    /** Every call but collecting, described, in order. */
    val calls = mutableListOf<String>()

    var onCall: suspend (String) -> Outcome<Unit> = { Outcome.Success(Unit) }

    fun setScheduled(
        chatId: Long,
        vararg messages: ScheduledMessage,
    ) {
        scheduled.value += chatId to messages.toList()
    }

    override fun scheduledMessages(chatId: Long): Flow<Outcome<ImmutableList<ScheduledMessage>>> {
        collected += chatId
        val error = loadError
        return scheduled
            .map<Map<Long, List<ScheduledMessage>>, Outcome<ImmutableList<ScheduledMessage>>> { all ->
                error ?: Outcome.Success(all[chatId].orEmpty().toImmutableList())
            }.distinctUntilChanged()
    }

    override suspend fun sendNow(
        chatId: Long,
        messageId: Long,
    ) = record("sendNow:$chatId:$messageId")

    override suspend fun reschedule(
        chatId: Long,
        messageId: Long,
        sendDate: Long,
        repeatPeriod: Int,
    ) = record("reschedule:$chatId:$messageId:$sendDate:$repeatPeriod")

    override suspend fun delete(
        chatId: Long,
        messageIds: List<Long>,
    ) = record("delete:$chatId:${messageIds.joinToString(",")}")

    private suspend fun record(call: String): Outcome<Unit> {
        calls += call
        return onCall(call)
    }
}
