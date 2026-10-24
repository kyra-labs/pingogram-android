package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.common.result.onSuccess
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.data.mapper.toTdFormattedText
import dev.kyralabs.pingogram.core.data.mapper.toTdMessageTopic
import dev.kyralabs.pingogram.core.data.repository.HistoryPage.Kind
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MessageRepository] on TDLib's chat history. Each history someone observes, or loaded recently (a chat's, or one of
 * its forum topics' or message threads', [HistoryKey]), has a window of loaded messages ([HistoryCache]) that TDLib's
 * updates keep current; [history] counts its collectors, so observed windows are
 * never evicted, and [trimMemory] drops the others.
 *
 * Loads ask TDLib for a page ([HistoryPages]) as `docs/PERFORMANCE.md` §3 says: first only what TDLib has locally,
 * then, when that is not enough, from the server. [loadOlder] asks for the page before the oldest loaded message,
 * [loadNewer] for the one after the newest, [loadNewest] for the newest page and [loadAround] for a page around a
 * message (`getChatHistory` with a negative offset, which returns messages on both sides of it), the last two replacing
 * the window. Loads of a chat run one at a time, shared by the callers that ask meanwhile, in the application scope
 * ([HistoryLoads]).
 *
 * [repliedMessage] serves the reply quotes of messages whose replied message is older than the loaded ones; the window
 * keeps what it fetched.
 *
 * [message] serves the composer's reply and edit bars from the window, or from TDLib.
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
        private val linkPreviews: SecretChatLinkPreviews,
    ) : MessageRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, HistoryCache(), HistoryCache::reduce)

        private val loads = HistoryLoads(store, scope)

        private val pages = HistoryPages(client)

        override fun history(
            chatId: Long,
            topic: MessageTopic?,
        ): Flow<ChatHistory> =
            flow {
                val key = HistoryKey(chatId, topic)
                store.update { cache -> cache.observing(key) }
                try {
                    emitAll(store.state.mapNotNull { cache -> cache.windows[key]?.history }.distinctUntilChanged())
                } finally {
                    store.update { cache -> cache.releasing(key) }
                }
            }

        override suspend fun loadOlder(
            chatId: Long,
            topic: MessageTopic?,
        ): Outcome<Unit> {
            val key = HistoryKey(chatId, topic)
            return loads.load(LoadKey(key, LoadKind.Older)) { window ->
                val oldestId = window.messages.lastOrNull()?.id
                when {
                    window.reachedStart -> Outcome.Success(null)
                    oldestId == null -> pages.newest(key)
                    else -> pages.older(key, oldestId)
                }
            }
        }

        override suspend fun loadNewer(
            chatId: Long,
            topic: MessageTopic?,
        ): Outcome<Unit> {
            val key = HistoryKey(chatId, topic)
            return loads.load(LoadKey(key, LoadKind.Newer)) { window ->
                val newestId = window.messages.firstOrNull()?.id
                if (window.reachedEnd || newestId == null) Outcome.Success(null) else pages.newer(key, newestId)
            }
        }

        override suspend fun loadAround(
            chatId: Long,
            messageId: Long,
            topic: MessageTopic?,
        ): Outcome<Unit> {
            val key = HistoryKey(chatId, topic)
            return loads.load(LoadKey(key, LoadKind.Around, messageId)) { window ->
                if (window.messages.indexOfId(messageId) >= 0) Outcome.Success(null) else pages.around(key, messageId)
            }
        }

        override suspend fun loadNewest(
            chatId: Long,
            topic: MessageTopic?,
        ): Outcome<Unit> {
            val key = HistoryKey(chatId, topic)
            return loads.load(LoadKey(key, LoadKind.Newest)) { window ->
                val reachesNewest = window.reachedEnd && (window.messages.isNotEmpty() || window.reachedStart)
                if (reachesNewest) Outcome.Success(null) else pages.newest(key)
            }
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
            store.state.value
                .windowsOf(chatId)
                .firstNotNullOfOrNull { it.repliedTo(messageId) }
                ?.let { return Outcome.Success(it) }
            val replied = client.request(TdApi.GetRepliedMessage(chatId, messageId)).map { it.toMessage() }
            replied.onSuccess { message -> store.update { cache -> cache.keepingReplied(chatId, message) } }
            return replied
        }

        /** The message from the chat's window, loaded or kept there, else `getMessage`. */
        override suspend fun message(
            chatId: Long,
            messageId: Long,
        ): Outcome<Message> {
            store.state.value
                .windowsOf(chatId)
                .firstNotNullOfOrNull { it.message(messageId) }
                ?.let { return Outcome.Success(it) }
            return client.request(TdApi.GetMessage(chatId, messageId)).map { it.toMessage() }
        }

        override fun trimMemory() = store.update { cache -> cache.trimmed() }

        /**
         * `sendMessage` of an `inputMessageText` that clears the draft, with TDLib's default link preview (none in a
         * secret chat unless allowed, [SecretChatLinkPreviews]) and
         * [options]' notification, scheduling and topic, replying to [replyToMessageId] of the chat itself without a
         * quote. TDLib puts the message into the chat as being sent (`updateNewMessage`) before it answers, so the
         * window shows it at once (`docs/PERFORMANCE.md` §1: tap to bubble ≤ 50 ms); `updateMessageSendSucceeded` or
         * `updateMessageSendFailed` replace it. The answer itself is not applied: the update has it.
         */
        override suspend fun sendText(
            chatId: Long,
            text: FormattedText,
            options: SendOptions,
            replyToMessageId: Long?,
        ): Outcome<Unit> {
            val content = TdApi.InputMessageText(text.toTdFormattedText(), linkPreviews.optionsFor(chatId), true)
            val replyTo = replyToMessageId?.let(::replyToMessage)
            return client
                .request(TdApi.SendMessage(chatId, options.tdTopic(), replyTo, options.toTdOptions(), null, content))
                .map {}
        }

        /**
         * `setChatDraftMessage` of the chat itself (no topic) with a text draft replying to [replyToMessageId], or none
         * for an empty [text] without a reply. TDLib dates the draft itself, and reports it in
         * `updateChatDraftMessage`.
         */
        override suspend fun setDraft(
            chatId: Long,
            text: FormattedText,
            replyToMessageId: Long?,
        ): Outcome<Unit> {
            val draft =
                if (text.text.isEmpty() && replyToMessageId == null) {
                    null
                } else {
                    TdApi.DraftMessage(
                        replyToMessageId?.let(::replyToMessage),
                        0,
                        TdApi.DraftMessageContentText(text.toTdFormattedText(), null),
                        0,
                        null,
                    )
                }
            return client.request(TdApi.SetChatDraftMessage(chatId, null, draft)).map {}
        }
    }

/**
 * A reply to the whole message [messageId] of the same chat, without a quote (td_api.tl `inputMessageReplyToMessage`:
 * no checklist task, no poll option).
 */
internal fun replyToMessage(messageId: Long) = TdApi.InputMessageReplyToMessage(messageId, null, 0, "")

/** The forum topic or message thread the message goes into (td_api.tl `sendMessage.topic_id`); null for the chat. */
internal fun SendOptions.tdTopic(): TdApi.MessageTopic? = topic?.toTdMessageTopic()

/**
 * `messageSendOptions` for [SendOptions]: silent disables the notification, a scheduled time holds the message until
 * then (`messageSchedulingStateSendAtDate`, not repeated); everything else is TDLib's default for a user's message.
 */
internal fun SendOptions.toTdOptions(): TdApi.MessageSendOptions =
    TdApi.MessageSendOptions().also { options ->
        options.disableNotification = silent
        options.schedulingState = scheduledAt?.let { TdApi.MessageSchedulingStateSendAtDate(it.toInt(), 0) }
    }
