package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.data.mapper.toInteractionInfo
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.data.mapper.toMessageContent
import dev.kyralabs.pingogram.core.data.mapper.toPoll
import dev.kyralabs.pingogram.core.data.mapper.toReplyMarkup
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.ReplyTo
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import org.drinkless.tdlib.TdApi

/** Windows kept at most, unless more chats are observed (`docs/PERFORMANCE.md` §3: an LRU of 3 chats). */
private const val MAX_WINDOWS = 3

/**
 * The state of [TdlibMessageRepository]: a [HistoryWindow] per history ([HistoryKey]: a chat, or one of its forum
 * topics or message threads), for the histories being observed, which are never evicted, and for the most recently
 * used others, up to [MAX_WINDOWS] windows in all; [clock] counts the uses. Updates of chats without a window are
 * ignored: their history is loaded when someone looks at it again. An update of a chat applies to each of its windows;
 * a new message joins only those of the topic it is in ([HistoryKey.holds]). `updatePoll` names no chat: the poll it
 * carries replaces the poll of every message that shows it, in every window, as a poll can be in several messages
 * (forwarded, in other chats).
 */
internal data class HistoryCache(
    val windows: PersistentMap<HistoryKey, HistoryWindow> = persistentMapOf(),
    val clock: Long = 0,
) {
    fun reduce(update: TdApi.Update): HistoryCache {
        val (keys, change) =
            if (update is TdApi.UpdatePoll) {
                windows.keys to HistoryChange.ReplacePoll(update.poll.toPoll())
            } else {
                val chatId = update.historyChatId()
                val change = chatId?.let { update.toHistoryChange() } ?: return this
                windows.keys.filter { it.chatId == chatId } to change
            }
        return keys
            .fold(this) { cache, key ->
                val within = change.within(key)
                if (within == null) cache else cache.changing(key) { window -> window.applying(within) }
            }
    }

    /** One more collector observes [key]. */
    fun observing(key: HistoryKey) = using(key) { it.copy(collectors = it.collectors + 1) }

    /** One collector of [key] stopped; the window stays, as the most recently used. */
    fun releasing(key: HistoryKey): HistoryCache =
        // Gone already if TDLib closed in between.
        if (key in windows) using(key) { it.copy(collectors = it.collectors - 1) } else this

    /** Load [loadId] of [key] starts; the window is created if missing (a history loaded before it is observed). */
    fun startingLoad(
        key: HistoryKey,
        loadId: Long,
    ) = using(key) { it.copy(loadId = loadId, changesWhileLoading = persistentListOf()) }

    /**
     * Load [loadId] of [key] ends with [page], null if it failed. Ignored if the window is not the one the load
     * started for: evicted, or TDLib closed, in between.
     */
    fun finishingLoad(
        key: HistoryKey,
        loadId: Long,
        page: HistoryPage?,
    ) = changing(key) { window -> if (window.loadId == loadId) window.finishingLoad(page) else window }

    /** Only the windows someone observes. */
    fun trimmed() = copy(windows = windows.filterValues { it.collectors > 0 }.toPersistentMap())

    /**
     * Keeps [message], fetched as the message a message of [chatId] replies to, with that chat's windows, if it has
     * any and the message belongs to the chat; a replied message of another chat gets none of this chat's updates.
     */
    fun keepingReplied(
        chatId: Long,
        message: Message,
    ) = if (message.chatId == chatId) {
        windows.keys
            .filter { it.chatId == chatId }
            .fold(this) { cache, key ->
                cache.changing(key) { window -> window.copy(replied = window.replied.putting(message.id, message)) }
            }
    } else {
        this
    }

    /** Replaces the window of [key], created if missing, with [transform] of it, as the most recent use. */
    private inline fun using(
        key: HistoryKey,
        transform: (HistoryWindow) -> HistoryWindow,
    ): HistoryCache {
        val window = transform(windows[key] ?: HistoryWindow()).copy(lastUsed = clock + 1)
        return copy(windows = windows.putting(key, window), clock = clock + 1).evicting()
    }

    /** Replaces the window of [key] with [transform] of it; the same instance if there is none, or no change. */
    private inline fun changing(
        key: HistoryKey,
        transform: (HistoryWindow) -> HistoryWindow,
    ): HistoryCache {
        val window = windows[key] ?: return this
        val changed = transform(window)
        return if (changed === window) this else copy(windows = windows.putting(key, changed))
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
        return copy(windows = evicted.fold(windows) { remaining, (key) -> remaining.removing(key) })
    }
}

/** The windows of chat [chatId]'s histories: the chat's own and its topics'. */
internal fun HistoryCache.windowsOf(chatId: Long): List<HistoryWindow> =
    windows.filterKeys { it.chatId == chatId }.values.toList()

/**
 * Which history a [HistoryWindow] holds: chat [chatId]'s own (every message of it), or that of its forum topic or
 * message thread [topic].
 */
internal data class HistoryKey(
    val chatId: Long,
    val topic: MessageTopic? = null,
) {
    /**
     * Whether [message], of this chat, belongs to this history: every message to the chat's own; to a topic's, those in
     * it (in the General topic also those without a topic); to a thread's, its replies, the message that starts it
     * excluded (its head, which the conversation shows itself).
     */
    fun holds(message: Message): Boolean =
        when (topic) {
            null -> true
            is MessageTopic.Forum -> message.topic == topic || (topic.isGeneral && message.topic == null)
            is MessageTopic.Thread -> message.topic == topic && message.id > topic.threadId
        }
}

/**
 * The loaded history of one chat: [messages] newest first (by id, descending), consecutive, without gaps;
 * [reachedStart] once they include its first message, [reachedEnd] while they include its newest one (true for an empty
 * window: nothing newer than nothing is missing). [collectors] counts who observes the window, [lastUsed] is the
 * [HistoryCache.clock] of its last use.
 *
 * While load [loadId] runs, the changes applied to the window are also kept in [changesWhileLoading], and applied again
 * once the page is merged: TDLib's answers and its updates reach the app on different paths, so an update TDLib sent
 * after computing the answer can arrive first, when the message it concerns is not loaded yet. Each change sets a
 * state rather than modifying one, so applying again one that came before the answer is harmless.
 *
 * [replied] holds the messages that loaded ones reply to, fetched because they are older than the loaded ones, by id;
 * the chat's updates keep them current like the [messages]. They go with the window.
 *
 * A window loaded around an older message ([HistoryPage.Kind.Replacing]) does not reach the newest message until newer
 * pages do: new messages stay out of it meanwhile, as messages between could be missing. [windowId] is the load that
 * last replaced loaded messages, 0 before any did ([ChatHistory.windowId]).
 */
internal data class HistoryWindow(
    val messages: PersistentList<Message> = persistentListOf(),
    val reachedStart: Boolean = false,
    val reachedEnd: Boolean = true,
    val collectors: Int = 0,
    val lastUsed: Long = 0,
    val loadId: Long? = null,
    val changesWhileLoading: PersistentList<HistoryChange> = persistentListOf(),
    val replied: PersistentMap<Long, Message> = persistentMapOf(),
    val windowId: Long = 0,
) {
    val history get() = ChatHistory(messages, reachedStart, reachedEnd, windowId)

    /** Applies [change], from one of the chat's updates, for this window ([HistoryChange.within]). */
    fun applying(change: HistoryChange): HistoryWindow {
        val changed = applyingNow(change)
        // Kept even if it changed nothing yet: it may concern a message of the page being loaded.
        return if (loadId == null) changed else changed.copy(changesWhileLoading = changesWhileLoading.adding(change))
    }

    /** Ends the load with [page], null if it failed, which leaves the messages as they are. */
    fun finishingLoad(page: HistoryPage?): HistoryWindow {
        val done = copy(loadId = null, changesWhileLoading = persistentListOf())
        if (page == null) return done
        val merged = done.merging(page)
        // Filling an empty window replaces no run of messages.
        val replacing = page.kind == HistoryPage.Kind.Replacing && messages.isNotEmpty()
        val replaced = if (replacing) merged.copy(windowId = loadId ?: windowId) else merged
        return changesWhileLoading.fold(replaced) { window, change -> window.applyingNow(change) }
    }

    private fun applyingNow(change: HistoryChange): HistoryWindow =
        when (change) {
            is HistoryChange.Put -> putting(change.message, change.replacedId)
            is HistoryChange.Edit -> editing(change.messageId, change.edit)
            is HistoryChange.Remove -> removing(change.messageIds)
            is HistoryChange.Unload -> unloading(change.messageIds)
            is HistoryChange.ReplacePoll -> replacingPoll(change.poll)
        }.applyingToReplied(change)

    /**
     * [change] on the [replied] messages: edits apply, and removed or unloaded ones go (TDLib sends no more updates of
     * unloaded ones; fetching them again brings them back). New messages never concern them.
     */
    private fun applyingToReplied(change: HistoryChange): HistoryWindow {
        if (replied.isEmpty()) return this
        val changed =
            when (change) {
                is HistoryChange.Put -> {
                    replied
                }

                is HistoryChange.Edit -> {
                    replied[change.messageId]?.let { replied.putting(change.messageId, change.edit(it)) } ?: replied
                }

                is HistoryChange.Remove -> {
                    replied.removingAll(change.messageIds)
                }

                is HistoryChange.Unload -> {
                    replied.removingAll(change.messageIds)
                }

                is HistoryChange.ReplacePoll -> {
                    replied.replacingPoll(change.poll)
                }
            }
        return if (changed == replied) this else copy(replied = changed)
    }

    /**
     * Puts [message] in its place by id, replacing [replacedId], a message being sent that got its final id or failed,
     * if it is loaded. A message older than every loaded one is left out while older ones are not loaded, and one newer
     * than every loaded one while newer ones are not, as messages between could be missing: loading pages brings it.
     */
    private fun putting(
        message: Message,
        replacedId: Long?,
    ): HistoryWindow {
        val replacedIndex = replacedId?.let(messages::indexOfId) ?: -1
        val oldestId = messages.lastOrNull()?.id
        val newestId = messages.firstOrNull()?.id
        val afterStart = reachedStart || (oldestId != null && message.id >= oldestId)
        val beforeEnd = reachedEnd || (newestId != null && message.id <= newestId)
        val belongs = replacedIndex >= 0 || (afterStart && beforeEnd)
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

    private fun replacingPoll(poll: Poll): HistoryWindow {
        val replaced = messages.replacingPoll(poll)
        return if (replaced === messages) this else copy(messages = replaced)
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

    /**
     * Adds the [page]'s messages to the loaded ones, or puts them in their place for a [HistoryPage.Kind.Replacing]
     * page. The loaded ones are as new as the changes applied to them, so only messages before or after them are added.
     */
    private fun merging(page: HistoryPage): HistoryWindow =
        when (page.kind) {
            HistoryPage.Kind.Older -> {
                val oldestId = messages.lastOrNull()?.id ?: Long.MAX_VALUE
                val older = page.messages.filter { it.id < oldestId }.sortedByDescending { it.id }
                copy(messages = messages.addingAll(older), reachedStart = page.reachedStart)
            }

            HistoryPage.Kind.Newer -> {
                val newestId = messages.firstOrNull()?.id ?: Long.MIN_VALUE
                val newer = page.messages.filter { it.id > newestId }.sortedByDescending { it.id }
                copy(messages = messages.mutate { it.addAll(0, newer) }, reachedEnd = page.reachedEnd)
            }

            HistoryPage.Kind.Replacing -> {
                val sorted = page.messages.distinctBy { it.id }.sortedByDescending { it.id }
                copy(
                    messages = sorted.toPersistentList(),
                    reachedStart = page.reachedStart,
                    reachedEnd = page.reachedEnd,
                )
            }
        }
}

/**
 * Messages of a chat, in any order: older than the loaded ones, newer than them, or ones that replace them (the newest
 * page, a page around a message); and whether the window then reaches the chat's first message ([reachedStart]) and
 * its newest one ([reachedEnd]). An older page keeps the window's [reachedEnd], a newer one its [reachedStart].
 */
internal class HistoryPage(
    val messages: List<Message>,
    val kind: Kind,
    val reachedStart: Boolean = false,
    val reachedEnd: Boolean = false,
) {
    enum class Kind {
        Older,
        Newer,
        Replacing,
    }
}

/** What an update changes in a chat's history. */
internal sealed interface HistoryChange {
    /**
     * This change for the window of [key]: a new message only for a history that holds it ([HistoryKey.holds]), or,
     * replacing a message being sent, as the removal of the one it replaces; null when it is none of the window's.
     */
    fun within(key: HistoryKey): HistoryChange? =
        when {
            this !is Put || key.holds(message) -> this
            replacedId != null -> Remove(setOf(replacedId))
            else -> null
        }

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

    /** [poll] changed (`updatePoll`): the messages that show it take it, whatever chat they are in. */
    class ReplacePoll(
        val poll: Poll,
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

        // The new content, if any, comes in its own updateMessageContent; the bot's buttons change with the edit.
        is TdApi.UpdateMessageEdited -> {
            val markup = replyMarkup?.toReplyMarkup()
            HistoryChange.Edit(messageId) { it.copy(editDate = editDate.toLong(), replyMarkup = markup) }
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

/**
 * The message that loaded message [messageId] replies to, if that is in the same chat and loaded or kept in
 * [HistoryWindow.replied]; null otherwise.
 */
internal fun HistoryWindow.repliedTo(messageId: Long): Message? {
    val message = messages.getOrNull(messages.indexOfId(messageId))
    val replyTo = (message?.replyTo as? ReplyTo.Message)?.takeIf { it.chatId == message.chatId } ?: return null
    return messages.getOrNull(messages.indexOfId(replyTo.messageId)) ?: replied[replyTo.messageId]
}

/** Message [id] of the chat if it is loaded or kept in [HistoryWindow.replied]; null otherwise. */
internal fun HistoryWindow.message(id: Long): Message? = messages.getOrNull(messages.indexOfId(id)) ?: replied[id]

/** The index of message [id] in [this] (newest first), else `-(insertion point) - 1`, as `binarySearch` answers. */
internal fun List<Message>.indexOfId(id: Long) = binarySearch { id.compareTo(it.id) }

private fun PersistentMap<Long, Message>.removingAll(ids: Set<Long>): PersistentMap<Long, Message> =
    if (ids.none(::containsKey)) this else mutate { map -> ids.forEach(map::remove) }
