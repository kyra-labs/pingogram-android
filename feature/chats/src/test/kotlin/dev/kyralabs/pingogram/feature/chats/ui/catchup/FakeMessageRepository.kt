package dev.kyralabs.pingogram.feature.chats.ui.catchup

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The histories Catch-up reads, as [setHistory] put them (newest first, as the repository keeps them); an unknown
 * chat's history is empty and not loaded. [loadNewest] only records the chat.
 */
class FakeMessageRepository : MessageRepository {
    private val histories = MutableStateFlow<Map<Long, ChatHistory>>(emptyMap())

    /** The chats whose newest messages were asked for, in order. */
    val loadedNewest = mutableListOf<Long>()

    fun setHistory(
        chatId: Long,
        vararg newestFirst: Message,
    ) {
        histories.value += chatId to ChatHistory(newestFirst.toList().toImmutableList(), reachedStart = true)
    }

    override fun history(
        chatId: Long,
        topic: MessageTopic?,
    ): Flow<ChatHistory> =
        histories
            .map { it[chatId] ?: ChatHistory(persistentListOf(), reachedStart = false, reachedEnd = false) }
            .distinctUntilChanged()

    override suspend fun loadNewest(
        chatId: Long,
        topic: MessageTopic?,
    ): Outcome<Unit> {
        loadedNewest += chatId
        return Outcome.Success(Unit)
    }

    override suspend fun loadOlder(
        chatId: Long,
        topic: MessageTopic?,
    ): Outcome<Unit> = unused()

    override suspend fun loadNewer(
        chatId: Long,
        topic: MessageTopic?,
    ): Outcome<Unit> = unused()

    override suspend fun loadAround(
        chatId: Long,
        messageId: Long,
        topic: MessageTopic?,
    ): Outcome<Unit> = unused()

    override suspend fun repliedMessage(
        chatId: Long,
        messageId: Long,
    ): Outcome<Message> = unused()

    override suspend fun message(
        chatId: Long,
        messageId: Long,
    ): Outcome<Message> = unused()

    override suspend fun loadFullRichMessage(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit> = unused()

    override fun trimMemory() = Unit

    override suspend fun sendText(
        chatId: Long,
        text: FormattedText,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ): Outcome<Unit> = unused()

    override suspend fun setDraft(
        chatId: Long,
        text: FormattedText,
        replyTo: ReplyTarget?,
    ): Outcome<Unit> = unused()
}

private fun unused(): Nothing = throw UnsupportedOperationException("Not used by Catch-up")
