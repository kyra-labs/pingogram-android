package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.data.repository.HistoryPage.Kind
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs the loads of [TdlibMessageRepository] on the windows of [store]: one load of a history at a time ([chatLocks]),
 * each owning the window's [HistoryWindow.loadId] until it ends, so the changes that arrive meanwhile are applied again
 * to its page; and every caller that asks for a load while it runs or waits shares it ([loads]). Loads run in [scope],
 * the application's, so a caller that leaves does not cancel one for the others waiting for it, and it still ends up in
 * the window; TDLib could not cancel the request anyway.
 */
internal class HistoryLoads(
    private val store: UpdateStore<HistoryCache>,
    private val scope: CoroutineScope,
) {
    /** The loads running or waiting, which concurrent calls of the same load share. */
    private val loads = ConcurrentHashMap<LoadKey, Deferred<Outcome<Unit>>>()

    /** One load of a history at a time. */
    private val chatLocks = ConcurrentHashMap<HistoryKey, Mutex>()

    /** Tells loads apart, across evictions and TDLib restarts. */
    private val loadIds = AtomicLong()

    /**
     * Runs load [key] once for every caller that asks while it runs or waits, after the other loads of its history,
     * merging the page [fetch] answers for the window: null when there is nothing to load. Nothing if the window is
     * evicted at once (while three other chats are observed).
     */
    suspend fun load(
        key: LoadKey,
        fetch: suspend (HistoryWindow) -> Outcome<HistoryPage?>,
    ): Outcome<Unit> {
        val load =
            loads.computeIfAbsent(key) {
                // Lazy, so it cannot finish, and remove itself, before it is in the map.
                scope.async(start = CoroutineStart.LAZY) {
                    try {
                        chatLocks.computeIfAbsent(key.history) { Mutex() }.withLock { loadPage(key.history, fetch) }
                    } finally {
                        loads.remove(key)
                    }
                }
            }
        load.start()
        return load.await()
    }

    private suspend fun loadPage(
        history: HistoryKey,
        fetch: suspend (HistoryWindow) -> Outcome<HistoryPage?>,
    ): Outcome<Unit> {
        val loadId = loadIds.incrementAndGet()
        store.update { cache -> cache.startingLoad(history, loadId) }
        val window =
            store.state.value.windows[history]
                ?.takeIf { it.loadId == loadId }
        val page = if (window == null) Outcome.Success(null) else fetch(window)
        store.update { cache -> cache.finishingLoad(history, loadId, page.getOrNull()) }
        return page.map {}
    }
}

/** What a load does, which with the history (and the message of [LoadKind.Around]) tells loads to share apart. */
internal enum class LoadKind {
    Older,
    Newer,
    Around,
    Newest,
}

internal data class LoadKey(
    val history: HistoryKey,
    val kind: LoadKind,
    val messageId: Long = 0,
)

/**
 * The pages of a history as TDLib answers them, as `docs/PERFORMANCE.md` §3 says: a chat's (`getChatHistory`) first
 * only what TDLib has locally, then, when that is not enough, from the server; a forum topic's
 * (`getForumTopicHistory`) and a message thread's (`getMessageThreadHistory`), which TDLib cannot answer locally only,
 * as TDLib answers them. Whether a page reaches the newest message comes from the last message of the chat (`getChat`,
 * which TDLib answers from memory), of the topic (`getForumTopic`) or of the thread (`getMessageThread`).
 */
internal class HistoryPages(
    private val client: TdlibClient,
) {
    /**
     * The newest messages, replacing the window: those TDLib has locally, else the server's, where none means that the
     * chat has none.
     */
    suspend fun newest(key: HistoryKey): Outcome<HistoryPage> =
        localFirst(key, fromMessageId = 0, offset = 0, accept = { it.isNotEmpty() })
            .map { (messages, local) ->
                val reachedStart = !local && messages.isEmpty()
                HistoryPage(messages, Kind.Replacing, reachedStart = reachedStart, reachedEnd = true)
            }

    /**
     * The messages before [fromMessageId]: those TDLib has locally, else the server's. TDLib can answer with fewer
     * messages than asked although there are more; only none from the server means that there are none.
     */
    suspend fun older(
        key: HistoryKey,
        fromMessageId: Long,
    ): Outcome<HistoryPage> =
        // Offset 0: from fromMessageId itself on, which is loaded already.
        localFirst(key, fromMessageId, offset = 0, accept = { page -> page.any { it.id < fromMessageId } })
            .map { (messages, local) ->
                val older = messages.filter { it.id < fromMessageId }
                HistoryPage(older, Kind.Older, reachedStart = !local && older.isEmpty())
            }

    /**
     * The messages after [fromMessageId]: those TDLib has locally, else the server's; the window then reaches the
     * newest message when they include the chat's last one, or when the server has none.
     */
    suspend fun newer(
        key: HistoryKey,
        fromMessageId: Long,
    ): Outcome<HistoryPage> {
        val lastMessageId = lastMessageId(key)
        return localFirst(
            key,
            fromMessageId,
            offset = -PAGE_SIZE,
            accept = { page -> page.any { it.id > fromMessageId } },
        ).map { (messages, local) ->
            val newer = messages.filter { it.id > fromMessageId }
            val reachedEnd = (!local && newer.isEmpty()) || newer.reaches(lastMessageId)
            HistoryPage(newer, Kind.Newer, reachedEnd = reachedEnd)
        }
    }

    /**
     * A page around [messageId], [AROUND_NEWER] of it newer, replacing the window: TDLib's local messages when they
     * hold it or lie on both sides of it, else the server's. It reaches the newest message when it includes the chat's
     * last one; the start only when the server has no messages there at all (an empty chat).
     */
    suspend fun around(
        key: HistoryKey,
        messageId: Long,
    ): Outcome<HistoryPage> {
        val lastMessageId = lastMessageId(key)
        val spans = { page: List<Message> ->
            page.any { it.id == messageId } || (page.any { it.id < messageId } && page.any { it.id > messageId })
        }
        return localFirst(key, messageId, offset = -AROUND_NEWER, accept = spans)
            .map { (messages, local) ->
                val empty = !local && messages.isEmpty()
                val reachedEnd = empty || messages.reaches(lastMessageId)
                HistoryPage(messages, Kind.Replacing, reachedStart = empty, reachedEnd = reachedEnd)
            }
    }

    /**
     * The history from [fromMessageId] with [offset]: a chat's first only what TDLib has locally, then, unless [accept]
     * takes that, from the server; a topic's as TDLib answers; with whether the answer is the local one.
     */
    private suspend fun localFirst(
        key: HistoryKey,
        fromMessageId: Long,
        offset: Int,
        accept: (List<Message>) -> Boolean,
    ): Outcome<Pair<List<Message>, Boolean>> {
        // A topic's history comes as TDLib has it, local or not.
        val local = if (key.topic == null) fetch(key, fromMessageId, offset, onlyLocal = true) else null
        return if (local != null && (local !is Outcome.Success || accept(local.value))) {
            local.map { it to true }
        } else {
            fetch(key, fromMessageId, offset, onlyLocal = false).map { it to false }
        }
    }

    /**
     * Up to a page of messages from [fromMessageId] (0: the newest) on, -[offset] of them newer than it, those the
     * history holds ([HistoryKey.holds]). td_api.tl allows null entries in the list.
     */
    private suspend fun fetch(
        key: HistoryKey,
        fromMessageId: Long,
        offset: Int,
        onlyLocal: Boolean,
    ): Outcome<List<Message>> {
        val request =
            when (val topic = key.topic) {
                null -> {
                    TdApi.GetChatHistory(key.chatId, fromMessageId, offset, PAGE_SIZE, onlyLocal)
                }

                is MessageTopic.Forum -> {
                    TdApi.GetForumTopicHistory(key.chatId, topic.topicId, fromMessageId, offset, PAGE_SIZE)
                }

                is MessageTopic.Thread -> {
                    TdApi.GetMessageThreadHistory(key.chatId, topic.threadId, fromMessageId, offset, PAGE_SIZE)
                }

                is MessageTopic.SavedMessages -> {
                    TdApi.GetSavedMessagesTopicHistory(topic.topicId, fromMessageId, offset, PAGE_SIZE)
                }
            }
        return client.request(request).map { messages ->
            messages.messages
                .filterNotNull()
                .map { it.toMessage() }
                .filter(key::holds)
        }
    }

    /**
     * The id of the last message of the history, from TDLib's memory where it has it: the chat's, the topic's, the
     * thread's last reply or the Saved Messages topic's newest message; 0 when it has none, and [UNKNOWN_LAST_MESSAGE]
     * when it cannot be fetched, which no page reaches: a newer page from the server then tells.
     */
    private suspend fun lastMessageId(key: HistoryKey): Long {
        val lastId =
            when (val topic = key.topic) {
                null -> {
                    client.request(TdApi.GetChat(key.chatId)).map { it.lastMessage?.id ?: 0 }
                }

                is MessageTopic.Forum -> {
                    client.request(TdApi.GetForumTopic(key.chatId, topic.topicId)).map { it.lastMessage?.id ?: 0 }
                }

                is MessageTopic.Thread -> {
                    client
                        .request(TdApi.GetMessageThread(key.chatId, topic.threadId))
                        .map { it.replyInfo?.lastMessageId ?: 0 }
                }

                // td_api.tl has no getSavedMessagesTopic: the newest message of its history.
                is MessageTopic.SavedMessages -> {
                    client
                        .request(TdApi.GetSavedMessagesTopicHistory(topic.topicId, 0, 0, 1))
                        .map { it.messages.firstOrNull()?.id ?: 0 }
                }
            }
        return lastId.getOrNull() ?: UNKNOWN_LAST_MESSAGE
    }
}

/** Whether these messages include the chat's last message [lastMessageId], or anything newer (0: the chat has none). */
private fun List<Message>.reaches(lastMessageId: Long): Boolean = lastMessageId == 0L || any { it.id >= lastMessageId }

/** Messages per page, `docs/PERFORMANCE.md` §3. */
private const val PAGE_SIZE = 50

/**
 * How many of a page around a message are newer than it: enough for a few screens of unread messages below the first
 * one, with older ones above to scroll back to.
 */
private const val AROUND_NEWER = 30

/** A last message id no page reaches. */
private const val UNKNOWN_LAST_MESSAGE = Long.MAX_VALUE
