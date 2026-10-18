package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.common.result.onSuccess
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.data.mapper.toTdFormattedText
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.SendOptions
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
 *
 * [repliedMessage] serves the reply quotes of messages whose replied message is older than the loaded ones; the window
 * keeps what it fetched.
 *
 * [sendText] and [setDraft] only send requests: the message being sent reaches the window through TDLib's updates, as
 * every other new message does, and the draft reaches the chat through `updateChatDraftMessage`.
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

        /**
         * The replied message from the chat's window, loaded or kept there, else `getRepliedMessage`, whose answer the
         * window keeps when it is a message of the chat. An update that overtakes that answer is not applied to it:
         * the quote may show the replied message as it was until its next change.
         */
        override suspend fun repliedMessage(
            chatId: Long,
            messageId: Long,
        ): Outcome<Message> {
            store.state.value.windows[chatId]
                ?.repliedTo(messageId)
                ?.let { return Outcome.Success(it) }
            val replied = client.request(TdApi.GetRepliedMessage(chatId, messageId)).map { it.toMessage() }
            replied.onSuccess { message -> store.update { cache -> cache.keepingReplied(chatId, message) } }
            return replied
        }

        override fun trimMemory() = store.update { cache -> cache.trimmed() }

        /**
         * `sendMessage` of an `inputMessageText` that clears the draft, with TDLib's default link preview and
         * [options]' notification and scheduling. TDLib puts the message into the chat as being sent
         * (`updateNewMessage`) before it answers, so the window shows it at once (`docs/PERFORMANCE.md` §1: tap to
         * bubble ≤ 50 ms); `updateMessageSendSucceeded` or `updateMessageSendFailed` replace it. The answer itself is
         * not applied: the update has it.
         */
        override suspend fun sendText(
            chatId: Long,
            text: FormattedText,
            options: SendOptions,
        ): Outcome<Unit> {
            val content = TdApi.InputMessageText(text.toTdFormattedText(), null, true)
            return client.request(TdApi.SendMessage(chatId, null, null, options.toTdOptions(), null, content)).map {}
        }

        /**
         * `setChatDraftMessage` of the chat itself (no topic) with a text draft, or none for an empty [text]. TDLib
         * dates the draft itself, and reports it in `updateChatDraftMessage`.
         */
        override suspend fun setDraft(
            chatId: Long,
            text: FormattedText,
        ): Outcome<Unit> {
            val draft =
                text.takeIf { it.text.isNotEmpty() }?.let {
                    TdApi.DraftMessage(null, 0, TdApi.DraftMessageContentText(it.toTdFormattedText(), null), 0, null)
                }
            return client.request(TdApi.SetChatDraftMessage(chatId, null, draft)).map {}
        }

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

/**
 * `messageSendOptions` for [SendOptions]: silent disables the notification, a scheduled time holds the message until
 * then (`messageSchedulingStateSendAtDate`, not repeated); everything else is TDLib's default for a user's message.
 */
private fun SendOptions.toTdOptions(): TdApi.MessageSendOptions =
    TdApi.MessageSendOptions().also { options ->
        options.disableNotification = silent
        options.schedulingState = scheduledAt?.let { TdApi.MessageSchedulingStateSendAtDate(it.toInt(), 0) }
    }

/** The messages before [fromMessageId], all of them for 0. td_api.tl allows null entries in the list. */
private fun TdApi.Messages.olderThan(fromMessageId: Long): List<Message> =
    messages.filterNotNull().filter { fromMessageId == 0L || it.id < fromMessageId }.map { it.toMessage() }
