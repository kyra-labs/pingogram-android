package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MessageRepository] on TDLib's chat history. Each chat someone observes, or loaded recently, has a window of loaded
 * messages ([HistoryCache]) that TDLib's updates keep current; [history] counts its collectors, so observed windows are
 * never evicted, and [trimMemory] drops the others.
 *
 * [loadOlder] asks TDLib for the page before the oldest loaded message as `docs/PERFORMANCE.md` §3 says: first
 * only what TDLib has locally, then, when that is nothing, from the server. The page is loaded in the application
 * scope, so a caller that leaves does not cancel it for the others waiting for it, and it still ends up in the window;
 * TDLib could not cancel the request anyway.
 */
@Singleton
class TdlibMessageRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope private val scope: CoroutineScope,
    ) : MessageRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, HistoryCache(), HistoryCache::reduce)

        /** The page loading for each chat, which concurrent [loadOlder] calls share. */
        private val loads = ConcurrentHashMap<Long, Deferred<Outcome<Unit>>>()

        /** Tells loads apart, across evictions and TDLib restarts. */
        private val loadIds = AtomicLong()

        override fun history(chatId: Long): Flow<ChatHistory> =
            flow {
                store.update { cache -> cache.observing(chatId) }
                try {
                    emitAll(store.state.mapNotNull { cache -> cache.windows[chatId]?.history }.distinctUntilChanged())
                } finally {
                    store.update { cache -> cache.releasing(chatId) }
                }
            }

        override suspend fun loadOlder(chatId: Long): Outcome<Unit> {
            val load =
                loads.computeIfAbsent(chatId) {
                    // Lazy, so it cannot finish, and remove itself, before it is in the map.
                    scope.async(start = CoroutineStart.LAZY) {
                        try {
                            loadPage(chatId)
                        } finally {
                            loads.remove(chatId)
                        }
                    }
                }
            load.start()
            return load.await()
        }

        override fun trimMemory() = store.update { cache -> cache.trimmed() }

        private suspend fun loadPage(chatId: Long): Outcome<Unit> {
            val loadId = loadIds.incrementAndGet()
            store.update { cache -> cache.startingLoad(chatId, loadId) }
            // Nothing to load if the window holds the whole history, or was evicted at once (while three other chats
            // are observed).
            val window =
                store.state.value.windows[chatId]
                    ?.takeIf { it.loadId == loadId }
            val page =
                when {
                    window == null || window.reachedStart -> Outcome.Success(null)
                    else -> fetchPage(chatId, fromMessageId = window.messages.lastOrNull()?.id ?: 0)
                }
            store.update { cache -> cache.finishingLoad(chatId, loadId, page.getOrNull()) }
            return page.map {}
        }

        /** The messages before [fromMessageId] (0: the newest ones): those TDLib has locally, else the server's. */
        private suspend fun fetchPage(
            chatId: Long,
            fromMessageId: Long,
        ): Outcome<HistoryPage> {
            val local = fetchOlder(chatId, fromMessageId, onlyLocal = true)
            if (local !is Outcome.Success || local.value.isNotEmpty()) {
                return local.map { messages -> HistoryPage(messages, reachedStart = false) }
            }
            // TDLib can answer with fewer messages than asked although there are more; only none from the server
            // means that there are none.
            val remote = fetchOlder(chatId, fromMessageId, onlyLocal = false)
            return remote.map { messages -> HistoryPage(messages, reachedStart = messages.isEmpty()) }
        }

        private suspend fun fetchOlder(
            chatId: Long,
            fromMessageId: Long,
            onlyLocal: Boolean,
        ): Outcome<List<Message>> =
            // Offset 0: from the message before fromMessageId on.
            client.request(TdApi.GetChatHistory(chatId, fromMessageId, 0, PAGE_SIZE, onlyLocal)).map { messages ->
                messages.olderThan(fromMessageId)
            }
    }

/** Messages per page, `docs/PERFORMANCE.md` §3. */
private const val PAGE_SIZE = 50

/** The messages before [fromMessageId], all of them for 0. td_api.tl allows null entries in the list. */
private fun TdApi.Messages.olderThan(fromMessageId: Long): List<Message> =
    messages.filterNotNull().filter { fromMessageId == 0L || it.id < fromMessageId }.map { it.toMessage() }
