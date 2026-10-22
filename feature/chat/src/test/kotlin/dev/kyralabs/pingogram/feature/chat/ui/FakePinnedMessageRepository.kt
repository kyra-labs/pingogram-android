package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.repository.HiddenPinsRepository
import dev.kyralabs.pingogram.core.domain.repository.PinnedMessageRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The pinned messages of each chat in memory, newest first, and every [unpinAll] call. */
class FakePinnedMessageRepository : PinnedMessageRepository {
    val pins = MutableStateFlow<Map<Long, List<Message>>>(emptyMap())

    val unpinnedAll = mutableListOf<Long>()

    /** Pins [messages] in chat [chatId], replacing its pins; newest first as the repository gives them. */
    fun setPinned(
        chatId: Long,
        vararg messages: Message,
    ) {
        pins.value += chatId to messages.sortedByDescending { it.id }
    }

    /** The topic of every [pinnedMessages] call. */
    val topics = mutableListOf<MessageTopic?>()

    override fun pinnedMessages(
        chatId: Long,
        topic: MessageTopic?,
    ): Flow<ImmutableList<Message>> {
        topics += topic
        return pins.map { it[chatId].orEmpty().toImmutableList() }.distinctUntilChanged()
    }

    override suspend fun unpinAll(chatId: Long): Outcome<Unit> {
        unpinnedAll += chatId
        return Outcome.Success(Unit)
    }
}

/** The hidden bars in memory, and what hiding answers. */
class FakeHiddenPinsRepository : HiddenPinsRepository {
    val hidden = MutableStateFlow<Map<Long, Long>>(emptyMap())

    var onHide: suspend (Long, Long) -> Outcome<Unit> = { chatId, messageId ->
        hidden.value += chatId to messageId
        Outcome.Success(Unit)
    }

    override fun hiddenUpTo(
        chatId: Long,
        topic: MessageTopic?,
    ): Flow<Long?> = hidden.map { it[chatId] }.distinctUntilChanged()

    override suspend fun hide(
        chatId: Long,
        messageId: Long,
        topic: MessageTopic?,
    ): Outcome<Unit> = onHide(chatId, messageId)
}
