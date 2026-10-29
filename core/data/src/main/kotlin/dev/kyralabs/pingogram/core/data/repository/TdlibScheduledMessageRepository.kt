package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.common.result.onSuccess
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toMessageContent
import dev.kyralabs.pingogram.core.data.mapper.toReplyMarkup
import dev.kyralabs.pingogram.core.data.mapper.toScheduledMessage
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageSchedule
import dev.kyralabs.pingogram.core.domain.model.ScheduledMessage
import dev.kyralabs.pingogram.core.domain.repository.ScheduledMessageRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [ScheduledMessageRepository] on TDLib, without a cache: each collection asks for the chat's scheduled messages
 * (`getChatScheduledMessages`) and then follows the chat's updates, as [TdlibPinnedMessageRepository] does.
 *
 * - **Order**: the collection subscribes to the updates before it asks, and applies those that arrive meanwhile after
 *   the answer, one at a time in TDLib's order. Each sets a state rather than changing one, so applying one the answer
 *   already reflects is harmless.
 * - **Updates**: `updateNewMessage` of a message with a scheduling state adds it (scheduled here or on another
 *   device); `updateMessageSendSucceeded` and `updateMessageSendFailed` replace a scheduled message being sent with
 *   its final one; `updateDeleteMessages` removes deleted ones (not those TDLib only unloaded from its memory), which
 *   is also how a scheduled message leaves once Telegram sent it: TDLib deletes it here and announces the sent message,
 *   with an id of its own and no scheduling state, with `updateNewMessage`, which the chat's history takes
 *   (`HistoryCache`); `updateMessageContent` and `updateMessageEdited` keep the bubbles current.
 * - **Changes made here** ([sendNow], [reschedule], [delete]) load the list again in every collection of the chat once
 *   TDLib took them: td_api.tl has no update for a message's new scheduling state, so a rescheduled message that kept
 *   its id would otherwise keep its old time.
 * - A failed load ends the collection with its failure; collecting again tries again.
 */
@AccountScoped
class TdlibScheduledMessageRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : ScheduledMessageRepository {
        /** The chats whose collections load their list again. */
        private val reloads = MutableSharedFlow<Long>(extraBufferCapacity = RELOAD_BUFFER)

        override fun scheduledMessages(chatId: Long): Flow<Outcome<ImmutableList<ScheduledMessage>>> =
            channelFlow<Outcome<ImmutableList<ScheduledMessage>>> {
                val changes = Channel<Change>(Channel.UNLIMITED)
                // UNDISPATCHED: subscribed before the request below is sent, so no update falls in between.
                val following =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        client.updates.collect { if (it.scheduledChatId() == chatId) changes.send(Change.Apply(it)) }
                    }
                val reloading =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        reloads.collect { if (it == chatId) changes.send(Change.Reload) }
                    }
                var messages =
                    when (val loaded = load(chatId)) {
                        is Outcome.Success -> {
                            loaded.value
                        }

                        is Outcome.Failure -> {
                            send(loaded)
                            following.cancel()
                            reloading.cancel()
                            return@channelFlow
                        }
                    }
                send(Outcome.Success(messages.sorted()))
                for (change in changes) {
                    messages =
                        when (change) {
                            is Change.Apply -> messages.applying(change.update)

                            // A reload that fails keeps what the list shows; the updates still follow.
                            Change.Reload -> load(chatId).let { (it as? Outcome.Success)?.value ?: messages }
                        }
                    send(Outcome.Success(messages.sorted()))
                }
            }.distinctUntilChanged()

        override suspend fun sendNow(
            chatId: Long,
            messageId: Long,
        ): Outcome<Unit> = changing(chatId, TdApi.EditMessageSchedulingState(chatId, messageId, null))

        override suspend fun reschedule(
            chatId: Long,
            messageId: Long,
            sendDate: Long,
            repeatPeriod: Int,
        ): Outcome<Unit> {
            val state = TdApi.MessageSchedulingStateSendAtDate(sendDate.toInt(), repeatPeriod.coerceAtLeast(0))
            return changing(chatId, TdApi.EditMessageSchedulingState(chatId, messageId, state))
        }

        // Scheduled messages are the user's own and reach nobody: revoking changes nothing.
        override suspend fun delete(
            chatId: Long,
            messageIds: List<Long>,
        ): Outcome<Unit> = changing(chatId, TdApi.DeleteMessages(chatId, messageIds.toLongArray(), true))

        /** Sends [function]; once TDLib took it, every collection of chat [chatId] loads its list again. */
        private suspend fun changing(
            chatId: Long,
            function: TdApi.Function<TdApi.Ok>,
        ): Outcome<Unit> = client.request(function).map {}.onSuccess { reloads.tryEmit(chatId) }

        /** The chat's scheduled messages by id; messages TDLib returns without a scheduling state are left out. */
        private suspend fun load(chatId: Long): Outcome<PersistentMap<Long, ScheduledMessage>> =
            client.request(TdApi.GetChatScheduledMessages(chatId)).map { found ->
                found.messages
                    .filterNotNull()
                    .mapNotNull { it.toScheduledMessage() }
                    .associateBy { it.message.id }
                    .toPersistentMap()
            }

        /** What the collection does next: apply an update of the chat, or load the list again. */
        private sealed interface Change {
            class Apply(
                val update: TdApi.Update,
            ) : Change

            data object Reload : Change
        }

        private companion object {
            /** Reloads waiting at most; one per change made here, and few are made at once. */
            const val RELOAD_BUFFER = 16
        }
    }

/** [this] (by id) after [update]. */
private fun PersistentMap<Long, ScheduledMessage>.applying(
    update: TdApi.Update,
): PersistentMap<Long, ScheduledMessage> =
    when (update) {
        is TdApi.UpdateNewMessage -> {
            update.message.toScheduledMessage()?.let { putting(it.message.id, it) } ?: this
        }

        // A scheduled message being sent gets its final id, or fails; one sent into the history is none of these.
        is TdApi.UpdateMessageSendSucceeded -> {
            replacing(update.oldMessageId, update.message)
        }

        is TdApi.UpdateMessageSendFailed -> {
            replacing(update.oldMessageId, update.message)
        }

        is TdApi.UpdateDeleteMessages -> {
            if (update.fromCache) this else update.messageIds.fold(this) { messages, id -> messages.removing(id) }
        }

        is TdApi.UpdateMessageContent -> {
            editing(update.messageId) { message ->
                message.copy(content = update.newContent.toMessageContent(message.sender, message.isChannelPost))
            }
        }

        is TdApi.UpdateMessageEdited -> {
            val markup = update.replyMarkup?.toReplyMarkup()
            editing(update.messageId) { it.copy(editDate = update.editDate.toLong(), replyMarkup = markup) }
        }

        else -> {
            this
        }
    }

/** Without [oldId], and with [message] if it is scheduled. */
private fun PersistentMap<Long, ScheduledMessage>.replacing(
    oldId: Long,
    message: TdApi.Message,
): PersistentMap<Long, ScheduledMessage> {
    val rest = removing(oldId)
    return message.toScheduledMessage()?.let { rest.putting(it.message.id, it) } ?: rest
}

private inline fun PersistentMap<Long, ScheduledMessage>.editing(
    id: Long,
    edit: (Message) -> Message,
): PersistentMap<Long, ScheduledMessage> {
    val scheduled = get(id) ?: return this
    return putting(id, scheduled.copy(message = edit(scheduled.message)))
}

/** The messages by when they go out: the earliest first, those sent when the other user comes online last. */
private fun Map<Long, ScheduledMessage>.sorted(): ImmutableList<ScheduledMessage> =
    values.sortedWith(compareBy<ScheduledMessage> { it.schedule.sendsAt }.thenBy { it.message.id }).toImmutableList()

/** When [this] schedule sends, for ordering; [Long.MAX_VALUE] for "when online", which has no time. */
private val MessageSchedule.sendsAt: Long
    get() =
        when (this) {
            is MessageSchedule.At -> sendDate
            is MessageSchedule.WhenVideoProcessed -> sendDate
            MessageSchedule.WhenOnline -> Long.MAX_VALUE
        }

/** The chat whose scheduled messages [this] can change; null for other updates. */
private fun TdApi.Update.scheduledChatId(): Long? =
    when (this) {
        is TdApi.UpdateNewMessage -> message.chatId
        is TdApi.UpdateMessageSendSucceeded -> message.chatId
        is TdApi.UpdateMessageSendFailed -> message.chatId
        is TdApi.UpdateDeleteMessages -> chatId
        is TdApi.UpdateMessageContent -> chatId
        is TdApi.UpdateMessageEdited -> chatId
        else -> null
    }
