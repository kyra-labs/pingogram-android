package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.data.mapper.toInteractionInfo
import dev.kyralabs.pingogram.core.data.mapper.toMessageContent
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.drinkless.tdlib.TdApi

/**
 * The posts [TdlibChannelFeedRepository] holds: those read for each channel ([read]) and those gone ([gone]), with the
 * updates TDLib sends for them taken in ([apply]): views and reactions, edits and new content change a post, and a
 * permanent deletion takes it away. A post known only as its channel's last one ([channels]) takes an update too.
 */
internal class ChannelFeedPosts {
    private val mutableRead = MutableStateFlow<Map<Long, List<Message>>>(emptyMap())
    private val mutableGone = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())

    /** The posts read for each channel, by chat id, newest first. */
    val read: StateFlow<Map<Long, List<Message>>> = mutableRead.asStateFlow()

    /** The posts deleted, or that TDLib no longer returns, by chat id: never shown again. */
    val gone: StateFlow<Map<Long, Set<Long>>> = mutableGone.asStateFlow()

    /** The channels as last seen, for an update to a post known only as a channel's last one. */
    @Volatile
    var channels: List<Chat> = emptyList()

    /** Changes the posts read with [change]. */
    fun updateRead(change: (Map<Long, List<Message>>) -> Map<Long, List<Message>>) = mutableRead.update(change)

    /** Puts [posts] of channel [chatId] in place of those with their ids, and adds those that are new. */
    fun put(
        chatId: Long,
        posts: List<Message>,
    ) = mutableRead.update { read -> read + (chatId to merged(read[chatId].orEmpty(), posts)) }

    /** Takes posts [ids] of channel [chatId] away for good. */
    fun drop(
        chatId: Long,
        ids: Collection<Long>,
    ) {
        if (ids.isNotEmpty()) mutableGone.update { it + (chatId to it[chatId].orEmpty() + ids) }
    }

    /** Takes [update] into the posts it concerns, if any is held. */
    fun apply(update: TdApi.Update) {
        when (update) {
            is TdApi.UpdateMessageInteractionInfo -> {
                val info = update.interactionInfo?.toInteractionInfo()
                edit(update.chatId, update.messageId) { it.copy(interactionInfo = info) }
            }

            is TdApi.UpdateMessageContent -> {
                edit(update.chatId, update.messageId) {
                    it.copy(content = update.newContent.toMessageContent(it.sender, it.isChannelPost))
                }
            }

            is TdApi.UpdateMessageEdited -> {
                edit(update.chatId, update.messageId) { it.copy(editDate = update.editDate.toLong()) }
            }

            // From the cache only: TDLib unloaded them, which does not delete them.
            is TdApi.UpdateDeleteMessages -> {
                if (!update.fromCache) drop(update.chatId, update.messageIds.toList())
            }

            else -> {
                // Nothing else concerns the posts held.
            }
        }
    }

    /** Changes post [messageId] of channel [chatId] with [change], whether read or known as its last post. */
    private fun edit(
        chatId: Long,
        messageId: Long,
        change: (Message) -> Message,
    ) {
        mutableRead.update { read ->
            val posts = read[chatId].orEmpty()
            val known =
                posts.firstOrNull { it.id == messageId }
                    ?: channels.firstOrNull { it.id == chatId }?.lastMessage?.takeIf { it.id == messageId }
                    ?: return@update read
            read + (chatId to merged(posts, listOf(change(known))))
        }
    }

    /** [posts] with [newer] in place of the posts with their ids, and added where new, newest first. */
    private fun merged(
        posts: List<Message>,
        newer: List<Message>,
    ): List<Message> {
        val ids = newer.map(Message::id).toSet()
        return (newer + posts.filterNot { it.id in ids }).sortedByDescending(Message::id)
    }
}
