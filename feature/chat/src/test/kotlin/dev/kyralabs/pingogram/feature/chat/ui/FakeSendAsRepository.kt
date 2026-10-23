package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AvailableSender
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.repository.SendAsRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Send-as: answers with what tests set, no senders by default, and records every chosen sender. */
class FakeSendAsRepository : SendAsRepository {
    var onAvailableSenders: suspend (Long) -> Outcome<ImmutableList<AvailableSender>> = {
        Outcome.Success(persistentListOf())
    }
    var onSetSender: suspend (MessageSender) -> Outcome<Unit> = { Outcome.Success(Unit) }

    /** The chat and sender of every [setSender] call. */
    val chosen = mutableListOf<Pair<Long, MessageSender>>()

    override suspend fun availableSenders(chatId: Long): Outcome<ImmutableList<AvailableSender>> =
        onAvailableSenders(chatId)

    override suspend fun setSender(
        chatId: Long,
        sender: MessageSender,
    ): Outcome<Unit> {
        chosen += chatId to sender
        return onSetSender(sender)
    }
}
