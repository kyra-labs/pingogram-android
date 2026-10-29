package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.data.mapper.toMessageContent
import dev.kyralabs.pingogram.core.data.mapper.toTdMessageTopic
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.repository.PinnedMessageRepository
import dev.kyralabs.pingogram.core.domain.repository.PinnedMessageRepository.Companion.MAX_PINNED
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [PinnedMessageRepository] on TDLib, without a cache: TDLib introduces no pinned messages by updates, so each
 * collection asks for them (`searchChatMessages` with `searchMessagesFilterPinned`) and then follows the chat's
 * updates.
 *
 * - **Order**: the collection subscribes to the updates before it asks, and applies those that arrive meanwhile after
 *   the answer, one at a time in TDLib's order. Each sets a state rather than changing one, so applying one the
 *   answer already reflects is harmless.
 * - **Updates**: `updateMessageIsPinned` adds a message (fetched with `getMessage`, kept only if it is still pinned
 *   then) or removes one; `updateDeleteMessages` removes deleted ones (not those TDLib only unloaded from its memory,
 *   which stay pinned); `updateMessageContent` and `updateMessageEdited` keep the bar's preview current.
 * - **Topics**: the pins of a forum topic or message thread are searched there; a new pin joins them only if it is
 *   in it.
 * - A failed answer reads as no pinned messages, which only hides the bar.
 */
@AccountScoped
class TdlibPinnedMessageRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : PinnedMessageRepository {
        override fun pinnedMessages(
            chatId: Long,
            topic: MessageTopic?,
        ): Flow<ImmutableList<Message>> =
            channelFlow<ImmutableList<Message>> {
                val updates = Channel<TdApi.Update>(Channel.UNLIMITED)
                // UNDISPATCHED: subscribed before the request below is sent, so no update falls in between.
                launch(start = CoroutineStart.UNDISPATCHED) {
                    client.updates.collect { if (it.pinsChatId() == chatId) updates.send(it) }
                }
                var pinned = load(chatId, topic)
                send(pinned)
                for (update in updates) {
                    pinned = applying(pinned, update, topic)
                    send(pinned)
                }
            }.distinctUntilChanged()

        override suspend fun unpinAll(chatId: Long): Outcome<Unit> =
            client.request(TdApi.UnpinAllChatMessages(chatId)).map {}

        /** The newest [MAX_PINNED] pinned messages (of [topic], if not null), newest first; none if TDLib fails. */
        private suspend fun load(
            chatId: Long,
            topic: MessageTopic?,
        ): PersistentList<Message> {
            val filter = TdApi.SearchMessagesFilterPinned()
            val search = TdApi.SearchChatMessages(chatId, topic?.toTdMessageTopic(), "", null, 0, 0, MAX_PINNED, filter)
            return client
                .request(search)
                .map { found -> found.messages.filterNotNull().map { it.toMessage() } }
                .getOrNull()
                .orEmpty()
                .sortedByDescending { it.id }
                .toPersistentList()
        }

        private suspend fun applying(
            pinned: PersistentList<Message>,
            update: TdApi.Update,
            topic: MessageTopic?,
        ): PersistentList<Message> =
            when (update) {
                is TdApi.UpdateMessageIsPinned -> {
                    val message =
                        if (update.isPinned) {
                            client.request(TdApi.GetMessage(update.chatId, update.messageId)).getOrNull()
                        } else {
                            null
                        }
                    val rest = pinned.removingAll { it.id == update.messageId }
                    val mapped = message?.takeIf { it.isPinned }?.toMessage()
                    if (mapped != null && (topic == null || mapped.topic == topic)) rest.inserting(mapped) else rest
                }

                is TdApi.UpdateDeleteMessages -> {
                    if (update.fromCache) pinned else pinned.removingAll { it.id in update.messageIds }
                }

                is TdApi.UpdateMessageContent -> {
                    pinned.editing(update.messageId) { message ->
                        message.copy(
                            content = update.newContent.toMessageContent(message.sender, message.isChannelPost),
                        )
                    }
                }

                is TdApi.UpdateMessageEdited -> {
                    pinned.editing(update.messageId) { it.copy(editDate = update.editDate.toLong()) }
                }

                else -> {
                    pinned
                }
            }
    }

/** The chat whose pinned messages [this] can change; null for other updates. */
private fun TdApi.Update.pinsChatId(): Long? =
    when (this) {
        is TdApi.UpdateMessageIsPinned -> chatId
        is TdApi.UpdateDeleteMessages -> chatId
        is TdApi.UpdateMessageContent -> chatId
        is TdApi.UpdateMessageEdited -> chatId
        else -> null
    }

/** [this] (newest first) with [message] in its place by id. */
private fun PersistentList<Message>.inserting(message: Message): PersistentList<Message> {
    val index = indexOfFirst { it.id < message.id }
    return if (index < 0) adding(message) else addingAt(index, message)
}

private inline fun PersistentList<Message>.editing(
    id: Long,
    edit: (Message) -> Message,
): PersistentList<Message> {
    val index = indexOfFirst { it.id == id }
    return if (index < 0) this else replacingAt(index, edit(get(index)))
}
