package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import kotlinx.coroutines.flow.Flow

/**
 * The history of chats, loaded a page at a time as the conversation scrolls back (`REQUIREMENTS.md` FR-C1) and kept up
 * to date with TDLib's updates, and what the composer sends and keeps as a draft (FR-C5). Only the chats being
 * observed and the most recently used others stay in memory (`docs/PERFORMANCE.md` §3); everything is dropped when
 * TDLib closes (logout).
 */
interface MessageRepository {
    /**
     * The loaded history of chat [chatId], then every change, for as long as it is collected. Observing loads nothing:
     * for a chat not loaded recently the first snapshot is empty without having reached the start, and [loadOlder]
     * loads the newest messages, so the caller sees every failure.
     */
    fun history(chatId: Long): Flow<ChatHistory>

    /**
     * Loads the messages before the oldest one in [history] (the newest messages if none is loaded) and succeeds once
     * they are there, or once there is nothing to load. A page can be short even when there are older messages; the
     * start is reached only when the server has none. Calls while a page of the same chat is loading share its
     * outcome. A failure (`DomainError.FloodWait`, `DomainError.Network`, `DomainError.NotFound`, …) leaves the
     * history as it was.
     */
    suspend fun loadOlder(chatId: Long): Outcome<Unit>

    /**
     * The message that message [messageId] of chat [chatId] replies to, for its reply quote when the replied message
     * is not in the loaded [history]: from memory when it was fetched before, else from TDLib, which may ask the
     * server. Replied messages of the same chat stay as up to date as the history while the chat is kept in memory.
     * Fails with `DomainError.NotFound` when the replied message was deleted, or [messageId] replies to nothing.
     */
    suspend fun repliedMessage(
        chatId: Long,
        messageId: Long,
    ): Outcome<Message>

    /**
     * Message [messageId] of chat [chatId], for the composer's reply and edit bars when it is not in the loaded
     * [history]: from memory when it is loaded, else from TDLib. Fails with `DomainError.NotFound` when it was deleted.
     */
    suspend fun message(
        chatId: Long,
        messageId: Long,
    ): Outcome<Message>

    /** Forgets the history of every chat nobody observes, for `ComponentCallbacks2.onTrimMemory`. */
    fun trimMemory()

    /**
     * Sends [text] to chat [chatId] as [options] say, as a reply to message [replyToMessageId] of the chat if it is not
     * null, and clears the chat's draft. Succeeds once TDLib accepted the message, which it has then put into the
     * [history] as being sent (a scheduled one stays out of it); whether it reaches Telegram shows in its sending state
     * there. Only the formatting a sender may set is sent (bold, italic, underline, strikethrough, spoiler, code, links
     * behind text, quotes); TDLib finds URLs, mentions and hashtags itself. Fails with `DomainError.FloodWait`,
     * `DomainError.Network` for a request that gets no answer in time, or another `DomainError` when TDLib refuses it
     * (no right to write, a text too long, a date too far ahead, …).
     */
    suspend fun sendText(
        chatId: Long,
        text: FormattedText,
        options: SendOptions = SendOptions(),
        replyToMessageId: Long? = null,
    ): Outcome<Unit>

    /**
     * Keeps [text], replying to message [replyToMessageId] of the chat if it is not null, as the draft of chat [chatId]
     * on every device of the account; an empty text without a reply removes the draft. The chat's draft (`Chat.draft`)
     * follows through TDLib's update.
     */
    suspend fun setDraft(
        chatId: Long,
        text: FormattedText,
        replyToMessageId: Long? = null,
    ): Outcome<Unit>
}
