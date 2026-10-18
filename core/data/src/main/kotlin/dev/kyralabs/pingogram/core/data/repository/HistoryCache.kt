package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.data.mapper.toInteractionInfo
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.data.mapper.toMessageContent
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import org.drinkless.tdlib.TdApi

/** Windows kept at most, unless more chats are observed (`docs/PERFORMANCE.md` §3: an LRU of 3 chats). */
private const val MAX_WINDOWS = 3

/**
 * The state of [TdlibMessageRepository]: a [HistoryWindow] per chat, for the chats being observed, which are never
 * evicted, and for the most recently used others, up to [MAX_WINDOWS] windows in all; [clock] counts the uses. Updates
 * of chats without a window are ignored: their history is loaded when someone looks at it again.
 */
internal data class HistoryCache(
    val windows: PersistentMap<Long, HistoryWindow> = persistentMapOf(),
    val clock: Long = 0,
) {
    fun reduce(update: TdApi.Update): HistoryCache {
        val chatId = update.historyChatId() ?: return this
        return changing(chatId) { window -> window.applying(update) }
    }

    /** One more collector observes [chatId]. */
    fun observing(chatId: Long) = using(chatId) { it.copy(collectors = it.collectors + 1) }

    /** One collector of [chatId] stopped; the window stays, as the most recently used. */
    fun releasing(chatId: Long): HistoryCache =
        // Gone already if TDLib closed in between.
        if (chatId in windows) using(chatId) { it.copy(collectors = it.collectors - 1) } else this

    /** Load [loadId] of [chatId] starts; the window is created if missing (a chat loaded before it is observed). */
    fun startingLoad(
        chatId: Long,
        loadId: Long,
    ) = using(chatId) { it.copy(loadId = loadId, changesWhileLoading = persistentListOf()) }

    /**
     * Load [loadId] of [chatId] ends with [page], null if it failed. Ignored if the window is not the one the load
     * started for: evicted, or TDLib closed, in between.
     */
    fun finishingLoad(
        chatId: Long,
        loadId: Long,
        page: HistoryPage?,
    ) = changing(chatId) { window -> if (window.loadId == loadId) window.finishingLoad(page) else window }

    /** Only the windows someone observes. */
    fun trimmed() = copy(windows = windows.filterValues { it.collectors > 0 }.toPersistentMap())

    /** Replaces the window of [chatId], created if missing, with [transform] of it, as the most recent use. */
    private inline fun using(
        chatId: Long,
        transform: (HistoryWindow) -> HistoryWindow,
    ): HistoryCache {
        val window = transform(windows[chatId] ?: HistoryWindow()).copy(lastUsed = clock + 1)
        return copy(windows = windows.putting(chatId, window), clock = clock + 1).evicting()
    }

    /** Replaces the window of [chatId] with [transform] of it; the same instance if there is none, or no change. */
    private inline fun changing(
        chatId: Long,
        transform: (HistoryWindow) -> HistoryWindow,
    ): HistoryCache {
        val window = windows[chatId] ?: return this
        val changed = transform(window)
        return if (changed === window) this else copy(windows = windows.putting(chatId, changed))
    }

    /** Drops the least recently used windows nobody observes while there are more than [MAX_WINDOWS]. */
    private fun evicting(): HistoryCache {
        val excess = windows.size - MAX_WINDOWS
        if (excess <= 0) return this
        val evicted =
            windows.entries
                .filter { it.value.collectors == 0 }
                .sortedBy { it.value.lastUsed }
                .take(excess)
        return copy(windows = evicted.fold(windows) { remaining, (chatId) -> remaining.removing(chatId) })
    }
}

/**
 * The loaded history of one chat: [messages] newest first (by id, descending) from the chat's newest message back,
 * without gaps; [reachedStart] once they include its first message. [collectors] counts who observes the window,
 * [lastUsed] is the [HistoryCache.clock] of its last use.
 *
 * While load [loadId] runs, the changes applied to the window are also kept in [changesWhileLoading], and applied again
 * once the page is merged: TDLib's answers and its updates reach the app on different paths, so an update TDLib sent
 * after computing the answer can arrive first, when the message it concerns is not loaded yet. Each change sets a
 * state rather than modifying one, so applying again one that came before the answer is harmless.
 *
 * Windows are anchored at the newest message for now. A window around an older message (jumping to a reply) would
 * also need to know whether it reaches the newest message, and a way to load newer pages.
 */
internal data class HistoryWindow(
    val messages: PersistentList<Message> = persistentListOf(),
    val reachedStart: Boolean = false,
    val collectors: Int = 0,
    val lastUsed: Long = 0,
    val loadId: Long? = null,
    val changesWhileLoading: PersistentList<HistoryChange> = persistentListOf(),
) {
    val history get() = ChatHistory(messages, reachedStart)

    /** Applies [update], one of the chat's updates ([historyChatId]). */
    fun applying(update: TdApi.Update): HistoryWindow =
        update.toHistoryChange()?.let { change ->
            val changed = applying(change)
            // Kept even if it changed nothing yet: it may concern a message of the page being loaded.
            if (loadId == null) changed else changed.copy(changesWhileLoading = changesWhileLoading.adding(change))
        } ?: this

    /** Ends the load with [page], null if it failed, which leaves the messages as they are. */
    fun finishingLoad(page: HistoryPage?): HistoryWindow {
        val done = copy(loadId = null, changesWhileLoading = persistentListOf())
        if (page == null) return done
        return changesWhileLoading.fold(done.merging(page)) { window, change -> window.applying(change) }
    }

    private fun applying(change: HistoryChange): HistoryWindow =
        when (change) {
            is HistoryChange.Put -> putting(change.message, change.replacedId)
            is HistoryChange.Edit -> editing(change.messageId, change.edit)
            is HistoryChange.Remove -> removing(change.messageIds)
            is HistoryChange.Unload -> unloading(change.messageIds)
        }

    /**
     * Puts [message] in its place by id, replacing [replacedId], a message being sent that got its final id or failed,
     * if it is loaded. A message older than every loaded one is left out while older ones are not loaded, as messages
     * between could be missing: loading older pages brings it.
     */
    private fun putting(
        message: Message,
        replacedId: Long?,
    ): HistoryWindow {
        val replacedIndex = replacedId?.let(messages::indexOfId) ?: -1
        val oldestId = messages.lastOrNull()?.id
        val belongs = replacedIndex >= 0 || reachedStart || (oldestId != null && message.id >= oldestId)
        if (!belongs) return this
        val rest = if (replacedIndex >= 0) messages.removingAt(replacedIndex) else messages
        return copy(messages = rest.upserting(message))
    }

    private fun editing(
        messageId: Long,
        edit: (Message) -> Message,
    ): HistoryWindow {
        val index = messages.indexOfId(messageId)
        if (index < 0) return this
        val edited = edit(messages[index])
        return if (edited == messages[index]) this else copy(messages = messages.replacingAt(index, edited))
    }

    private fun removing(messageIds: Set<Long>): HistoryWindow =
        when {
            messages.none { it.id in messageIds } -> this
            else -> copy(messages = messages.removingAll { it.id in messageIds })
        }

    /**
     * TDLib unloaded [messageIds] from its memory (`updateDeleteMessages.from_cache`, sent for chats that are not
     * open): they still exist, but TDLib sends no updates about them until it returns them again, so they would go
     * stale here. They are dropped with every older message, which keeps the window without gaps; loading older pages
     * brings them back. Applied again after a page, this may drop messages the page brought back, which costs a load.
     */
    private fun unloading(messageIds: Set<Long>): HistoryWindow {
        // Newest first, so this is the newest unloaded message.
        val index = messages.indexOfFirst { it.id in messageIds }
        if (index < 0) return this
        return copy(messages = messages.mutate { it.subList(index, it.size).clear() }, reachedStart = false)
    }

    /** Adds the [page]'s messages older than the loaded ones, which are as new as the changes applied to them. */
    private fun merging(page: HistoryPage): HistoryWindow {
        val oldestId = messages.lastOrNull()?.id ?: Long.MAX_VALUE
        val older = page.messages.filter { it.id < oldestId }.sortedByDescending { it.id }
        return copy(messages = messages.addingAll(older), reachedStart = page.reachedStart)
    }
}

/** Messages of a chat older than the loaded ones, in any order, and whether they reach the chat's first message. */
internal class HistoryPage(
    val messages: List<Message>,
    val reachedStart: Boolean,
)

/** What an update changes in a chat's history. */
internal sealed interface HistoryChange {
    /** [message] is new, or replaces [replacedId]. */
    class Put(
        val message: Message,
        val replacedId: Long?,
    ) : HistoryChange

    /** Message [messageId] becomes [edit] of itself. */
    class Edit(
        val messageId: Long,
        val edit: (Message) -> Message,
    ) : HistoryChange

    /** [messageIds] are gone for the user: deleted, or no longer accessible. */
    class Remove(
        val messageIds: Set<Long>,
    ) : HistoryChange

    /** TDLib unloaded [messageIds] from its memory; see [HistoryWindow]'s `unloading`. */
    class Unload(
        val messageIds: Set<Long>,
    ) : HistoryChange
}

/** The chat whose history [this] changes; null for other updates. */
private fun TdApi.Update.historyChatId(): Long? =
    when (this) {
        is TdApi.UpdateNewMessage -> message.chatId
        is TdApi.UpdateMessageSendSucceeded -> message.chatId
        is TdApi.UpdateMessageSendFailed -> message.chatId
        is TdApi.UpdateMessageContent -> chatId
        is TdApi.UpdateMessageEdited -> chatId
        is TdApi.UpdateMessageIsPinned -> chatId
        is TdApi.UpdateMessageInteractionInfo -> chatId
        is TdApi.UpdateMessageContentOpened -> chatId
        is TdApi.UpdateMessageMentionRead -> chatId
        is TdApi.UpdateDeleteMessages -> chatId
        else -> null
    }

/**
 * The change [this] makes to the history of its chat. The other `updateMessage*` updates concern what [Message] does
 * not hold: unread reactions and poll votes, fact-checks, suggested posts, ephemeral content, send acknowledgements,
 * live location views, and reactions for bots.
 */
private fun TdApi.Update.toHistoryChange(): HistoryChange? =
    when (this) {
        is TdApi.UpdateNewMessage -> {
            message.toPut(replacedId = null)
        }

        // The message gets a new id in both cases (td_api.tl: old_message_id is the temporary one).
        is TdApi.UpdateMessageSendSucceeded -> {
            message.toPut(oldMessageId)
        }

        is TdApi.UpdateMessageSendFailed -> {
            message.toPut(oldMessageId)
        }

        is TdApi.UpdateMessageContent -> {
            // The sender and the channel flag only matter for service messages.
            HistoryChange.Edit(messageId) { message ->
                message.copy(content = newContent.toMessageContent(message.sender, message.isChannelPost))
            }
        }

        // The new content, if any, comes in its own updateMessageContent.
        is TdApi.UpdateMessageEdited -> {
            HistoryChange.Edit(messageId) { it.copy(editDate = editDate.toLong()) }
        }

        is TdApi.UpdateMessageIsPinned -> {
            HistoryChange.Edit(messageId) { it.copy(isPinned = isPinned) }
        }

        is TdApi.UpdateMessageInteractionInfo -> {
            val info = interactionInfo?.toInteractionInfo()
            HistoryChange.Edit(messageId) { it.copy(interactionInfo = info) }
        }

        // It also starts the self-destruct timer, which Message does not hold.
        is TdApi.UpdateMessageContentOpened -> {
            HistoryChange.Edit(messageId) { it.copy(content = it.content.opened()) }
        }

        is TdApi.UpdateMessageMentionRead -> {
            HistoryChange.Edit(messageId) { it.copy(containsUnreadMention = false) }
        }

        // Not permanent: inaccessible now (e.g. history hidden from new members, a chat left), which is just as gone.
        is TdApi.UpdateDeleteMessages -> {
            val ids = messageIds.toSet()
            if (fromCache) HistoryChange.Unload(ids) else HistoryChange.Remove(ids)
        }

        else -> {
            null
        }
    }

/** Puts this message, unless it is scheduled: scheduled messages are not part of the history (getChatHistory). */
private fun TdApi.Message.toPut(replacedId: Long?): HistoryChange =
    if (schedulingState == null) {
        HistoryChange.Put(toMessage(), replacedId)
    } else {
        HistoryChange.Remove(setOfNotNull(replacedId))
    }

/** The content once opened (TDLib's update_opened_message_content): voice notes listened, video notes viewed. */
private fun MessageContent.opened(): MessageContent =
    when (this) {
        is MessageContent.VoiceNote -> copy(isListened = true)
        is MessageContent.VideoNote -> copy(isViewed = true)
        else -> this
    }

/** [this] (newest first) with [message] in its place by id, replacing the message with the same id. */
private fun PersistentList<Message>.upserting(message: Message): PersistentList<Message> {
    val index = indexOfId(message.id)
    return if (index >= 0) replacingAt(index, message) else addingAt(-(index + 1), message)
}

/** The index of message [id] in [this] (newest first), else `-(insertion point) - 1`, as `binarySearch` answers. */
private fun List<Message>.indexOfId(id: Long) = binarySearch { id.compareTo(it.id) }
