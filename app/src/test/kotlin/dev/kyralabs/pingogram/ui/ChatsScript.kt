package dev.kyralabs.pingogram.ui

import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.flow.MutableStateFlow
import org.drinkless.tdlib.TdApi
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Three private chats as TDLib announces them while the main chat list loads, with their users and a short history
 * each, scripted on a [FakeTdlibClient] for the tests of the Chats tab that run the real repositories: `getChat`, and
 * `getChatHistory` from any message with TDLib's offsets (a conversation with unread messages opens around the last
 * one read). Opening and closing a chat succeed; the archive and every later page of the main list are empty.
 */
internal object ChatsScript {
    const val ADA = 101L
    const val GRACE = 102L
    const val ALAN = 103L

    const val ADA_TITLE = "Ada Lovelace"
    const val GRACE_TITLE = "Grace Hopper"

    /** An older message of each chat: its bubble shows only in the conversation, never in the chat's row. */
    val olderMessage = mapOf(ADA to "The engine computes Bernoulli numbers", GRACE to "Found a moth in relay 70")

    /** Wednesday, 15 November 2023, 14:00 UTC: the newest message is that morning, the others the day before. */
    val now: Instant = Instant.parse("2023-11-15T14:00:00Z")

    private const val ME = 1L
    private const val MESSAGE_IDS = 1_000L
    private const val HOUR_SECONDS = 3_600L

    /** Far enough apart that the history spans two days, for a date separator. */
    private const val HOURS_APART = 12L
    private val NEWEST_MESSAGE: Instant = Instant.parse("2023-11-15T09:41:00Z")

    fun install(tdlib: FakeTdlibClient) {
        val loaded = AtomicBoolean(false)
        tdlib.on(TdApi.LoadChats::class) { request ->
            if (request.chatList is TdApi.ChatListMain && loaded.compareAndSet(false, true)) {
                users.forEach { tdlib.emit(TdApiFixtures.updateUser(it)) }
                chats.forEach { tdlib.emit(TdApiFixtures.updateNewChat(it)) }
                TdResult.Success(TdApi.Ok())
            } else {
                // TDLib's answer once a chat list is loaded completely.
                TdResult.Failure(TdError(404, "Not Found"))
            }
        }
        tdlib.on(TdApi.GetChatHistory::class) { request ->
            val messages =
                histories[request.chatId].orEmpty().page(
                    request.fromMessageId,
                    request.offset,
                    request.limit,
                )
            TdResult.Success(TdApi.Messages(messages.size, messages.toTypedArray()))
        }
        tdlib.on(TdApi.GetChat::class) { request ->
            chats.firstOrNull { it.id == request.chatId }?.let { TdResult.Success(it) }
                ?: TdResult.Failure(TdError(404, "Not Found"))
        }
        tdlib.on(TdApi.OpenChat::class, TdResult.Success(TdApi.Ok()))
        tdlib.on(TdApi.CloseChat::class, TdResult.Success(TdApi.Ok()))
    }

    /** The `openChat` and `closeChat` requests sent, in order: `+101` opened chat 101, `-101` closed it. */
    fun openAndClose(tdlib: FakeTdlibClient): List<String> =
        tdlib.sent.mapNotNull { function ->
            when (function) {
                is TdApi.OpenChat -> "+${function.chatId}"
                is TdApi.CloseChat -> "-${function.chatId}"
                else -> null
            }
        }

    private val users =
        listOf(
            TdApiFixtures.user(id = ADA, firstName = "Ada", lastName = "Lovelace"),
            TdApiFixtures.user(id = GRACE, firstName = "Grace", lastName = "Hopper"),
            TdApiFixtures.user(id = ALAN, firstName = "Alan", lastName = "Turing"),
        )

    /** Newest first, as TDLib answers `getChatHistory`. */
    private val histories: Map<Long, List<TdApi.Message>> =
        mapOf(
            ADA to
                history(
                    ADA,
                    "See you at the lab tomorrow" to true,
                    olderMessage.getValue(ADA) to false,
                    "Did you read my notes on the engine?" to false,
                ),
            GRACE to
                history(
                    GRACE,
                    "Nanoseconds are 30 cm of wire" to false,
                    olderMessage.getValue(GRACE) to true,
                ),
            ALAN to history(ALAN, "Can machines think?" to false),
        )

    private val chats =
        listOf(
            chat(ADA, ADA_TITLE, order = 3, unreadCount = 0),
            chat(GRACE, GRACE_TITLE, order = 2, unreadCount = 1),
            chat(ALAN, "Alan Turing", order = 1, unreadCount = 0),
        )

    private fun chat(
        id: Long,
        title: String,
        order: Long,
        unreadCount: Int,
    ): TdApi.Chat {
        val history = histories.getValue(id)
        return TdApiFixtures.chat(
            id = id,
            type = TdApi.ChatTypePrivate(id),
            title = title,
            lastMessage = history.first(),
            positions = arrayOf(TdApiFixtures.chatPosition(order = order)),
            unreadCount = unreadCount,
            lastReadInboxMessageId = history.getOrNull(unreadCount)?.id ?: 0,
        )
    }

    /**
     * The page of this history (newest first) that `getChatHistory` answers: from message [fromMessageId] (0: the
     * newest) on, older ones after it, [offset] (0 or negative) newer ones before it, at most [limit].
     */
    private fun List<TdApi.Message>.page(
        fromMessageId: Long,
        offset: Int,
        limit: Int,
    ): List<TdApi.Message> {
        val from = if (fromMessageId == 0L) 0 else indexOfFirst { it.id <= fromMessageId }.takeIf { it >= 0 } ?: size
        return drop((from + offset).coerceAtLeast(0)).take(limit)
    }

    /** [texts] newest first, each with whether the current user sent it, an hour apart, the newest this morning. */
    private fun history(
        chatId: Long,
        vararg texts: Pair<String, Boolean>,
    ): List<TdApi.Message> =
        texts.mapIndexed { index, (text, isOutgoing) ->
            TdApiFixtures.message(
                id = chatId * MESSAGE_IDS + texts.size - index,
                chatId = chatId,
                sender = TdApi.MessageSenderUser(if (isOutgoing) ME else chatId),
                date = (NEWEST_MESSAGE.epochSecond - index * HOUR_SECONDS * HOURS_APART).toInt(),
                content = TdApiFixtures.messageText(text),
                isOutgoing = isOutgoing,
            )
        }
}

/** A wall clock stopped at [ChatsScript.now] in UTC, so times and dates in screenshots never change. */
internal class StoppedWallClock : WallClock {
    override fun now(): Instant = ChatsScript.now

    override fun zone(): ZoneId = ZoneOffset.UTC

    override val minutes = MutableStateFlow(ChatsScript.now)
}
