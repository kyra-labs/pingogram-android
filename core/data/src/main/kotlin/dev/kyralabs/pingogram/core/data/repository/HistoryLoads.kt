package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.data.repository.HistoryPage.Kind
import dev.kyralabs.pingogram.core.domain.model.Message
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
 * Runs the loads of [TdlibMessageRepository] on the windows of [store]: one load of a chat at a time ([chatLocks]),
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

    /** One load of a chat at a time. */
    private val chatLocks = ConcurrentHashMap<Long, Mutex>()

    /** Tells loads apart, across evictions and TDLib restarts. */
    private val loadIds = AtomicLong()

    /**
     * Runs load [key] once for every caller that asks while it runs or waits, after the other loads of its chat,
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
                        chatLocks.computeIfAbsent(key.chatId) { Mutex() }.withLock { loadPage(key.chatId, fetch) }
                    } finally {
                        loads.remove(key)
                    }
                }
            }
        load.start()
        return load.await()
    }

    private suspend fun loadPage(
        chatId: Long,
        fetch: suspend (HistoryWindow) -> Outcome<HistoryPage?>,
    ): Outcome<Unit> {
        val loadId = loadIds.incrementAndGet()
        store.update { cache -> cache.startingLoad(chatId, loadId) }
        val window =
            store.state.value.windows[chatId]
                ?.takeIf { it.loadId == loadId }
        val page = if (window == null) Outcome.Success(null) else fetch(window)
        store.update { cache -> cache.finishingLoad(chatId, loadId, page.getOrNull()) }
        return page.map {}
    }
}

/** What a load does, which with the chat (and the message of [LoadKind.Around]) tells loads to share apart. */
internal enum class LoadKind {
    Older,
    Newer,
    Around,
    Newest,
}

internal data class LoadKey(
    val chatId: Long,
    val kind: LoadKind,
    val messageId: Long = 0,
)

/**
 * The pages of a chat's history as TDLib answers them (`getChatHistory`), as `docs/PERFORMANCE.md` §3 says: first
 * only what TDLib has locally, then, when that is not enough, from the server. Whether a page reaches the chat's newest
 * message comes from the chat's last message (`getChat`, which TDLib answers from memory).
 */
internal class HistoryPages(
    private val client: TdlibClient,
) {
    /**
     * The newest messages, replacing the window: those TDLib has locally, else the server's, where none means that the
     * chat has none.
     */
    suspend fun newest(chatId: Long): Outcome<HistoryPage> =
        localFirst(chatId, fromMessageId = 0, offset = 0, accept = { it.isNotEmpty() })
            .map { (messages, local) ->
                val reachedStart = !local && messages.isEmpty()
                HistoryPage(messages, Kind.Replacing, reachedStart = reachedStart, reachedEnd = true)
            }

    /**
     * The messages before [fromMessageId]: those TDLib has locally, else the server's. TDLib can answer with fewer
     * messages than asked although there are more; only none from the server means that there are none.
     */
    suspend fun older(
        chatId: Long,
        fromMessageId: Long,
    ): Outcome<HistoryPage> =
        // Offset 0: from fromMessageId itself on, which is loaded already.
        localFirst(chatId, fromMessageId, offset = 0, accept = { page -> page.any { it.id < fromMessageId } })
            .map { (messages, local) ->
                val older = messages.filter { it.id < fromMessageId }
                HistoryPage(older, Kind.Older, reachedStart = !local && older.isEmpty())
            }

    /**
     * The messages after [fromMessageId]: those TDLib has locally, else the server's; the window then reaches the
     * newest message when they include the chat's last one, or when the server has none.
     */
    suspend fun newer(
        chatId: Long,
        fromMessageId: Long,
    ): Outcome<HistoryPage> {
        val lastMessageId = lastMessageId(chatId)
        return localFirst(
            chatId,
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
        chatId: Long,
        messageId: Long,
    ): Outcome<HistoryPage> {
        val lastMessageId = lastMessageId(chatId)
        val spans = { page: List<Message> ->
            page.any { it.id == messageId } || (page.any { it.id < messageId } && page.any { it.id > messageId })
        }
        return localFirst(chatId, messageId, offset = -AROUND_NEWER, accept = spans)
            .map { (messages, local) ->
                val empty = !local && messages.isEmpty()
                val reachedEnd = empty || messages.reaches(lastMessageId)
                HistoryPage(messages, Kind.Replacing, reachedStart = empty, reachedEnd = reachedEnd)
            }
    }

    /**
     * `getChatHistory` from [fromMessageId] with [offset], first only what TDLib has locally, then, unless [accept]
     * takes that, from the server; with whether the answer is the local one.
     */
    private suspend fun localFirst(
        chatId: Long,
        fromMessageId: Long,
        offset: Int,
        accept: (List<Message>) -> Boolean,
    ): Outcome<Pair<List<Message>, Boolean>> {
        val local = fetch(chatId, fromMessageId, offset, onlyLocal = true)
        if (local !is Outcome.Success || accept(local.value)) return local.map { it to true }
        return fetch(chatId, fromMessageId, offset, onlyLocal = false).map { it to false }
    }

    /**
     * Up to a page of messages from [fromMessageId] (0: the newest) on, -[offset] of them newer than it. td_api.tl
     * allows null entries in the list.
     */
    private suspend fun fetch(
        chatId: Long,
        fromMessageId: Long,
        offset: Int,
        onlyLocal: Boolean,
    ): Outcome<List<Message>> =
        client.request(TdApi.GetChatHistory(chatId, fromMessageId, offset, PAGE_SIZE, onlyLocal)).map { messages ->
            messages.messages.filterNotNull().map { it.toMessage() }
        }

    /**
     * The id of the chat's last message, from TDLib's memory; 0 when it has none, and [UNKNOWN_LAST_MESSAGE] when the
     * chat cannot be fetched, which no page reaches: a newer page from the server then tells.
     */
    private suspend fun lastMessageId(chatId: Long): Long {
        val chat = client.request(TdApi.GetChat(chatId)).getOrNull() ?: return UNKNOWN_LAST_MESSAGE
        return chat.lastMessage?.id ?: 0
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
