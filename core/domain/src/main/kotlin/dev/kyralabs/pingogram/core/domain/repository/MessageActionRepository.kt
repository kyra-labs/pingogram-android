package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AvailableReactions
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageProperties
import dev.kyralabs.pingogram.core.domain.model.ReactionType

/**
 * What the user does to a message of the conversation (`REQUIREMENTS.md` FR-C6, FR-C7): what TDLib allows for it, its
 * reactions, editing, deleting, pinning and sending it again. Nothing is kept here: the changes reach the message
 * through TDLib's updates, which `MessageRepository.history` applies.
 *
 * Every call fails with a `DomainError`: `FloodWait`, `Network` for a request that gets no answer in time, `NotFound`
 * for a message or chat TDLib does not know, or another one when TDLib refuses it.
 */
interface MessageActionRepository {
    /** What the user can do with message [messageId] of chat [chatId] right now; TDLib answers from memory. */
    suspend fun properties(
        chatId: Long,
        messageId: Long,
    ): Outcome<MessageProperties>

    /** The reactions the user can add to message [messageId] of chat [chatId]; none where reactions are off. */
    suspend fun availableReactions(
        chatId: Long,
        messageId: Long,
    ): Outcome<AvailableReactions>

    /**
     * Adds the user's [reaction] to message [messageId] of chat [chatId], without the big animation, and to the
     * recently used reactions. TDLib shows it at once and takes it back if Telegram refuses it.
     */
    suspend fun addReaction(
        chatId: Long,
        messageId: Long,
        reaction: ReactionType,
    ): Outcome<Unit>

    /** Takes the user's [reaction] off message [messageId] of chat [chatId]. */
    suspend fun removeReaction(
        chatId: Long,
        messageId: Long,
        reaction: ReactionType,
    ): Outcome<Unit>

    /**
     * Replaces the text of the text message [messageId] of chat [chatId] with [text]; only the formatting a sender may
     * set is sent, as for a new message. Succeeds once Telegram has the edit.
     */
    suspend fun editText(
        chatId: Long,
        messageId: Long,
        text: FormattedText,
    ): Outcome<Unit>

    /**
     * Replaces the caption of the media message [messageId] of chat [chatId] with [caption] (an empty one removes it),
     * the caption above the media if [showCaptionAboveMedia]. Succeeds once Telegram has the edit.
     */
    suspend fun editCaption(
        chatId: Long,
        messageId: Long,
        caption: FormattedText,
        showCaptionAboveMedia: Boolean,
    ): Outcome<Unit>

    /**
     * Deletes [messageIds] of chat [chatId]: for everyone in the chat if [forEveryone], else for the user only
     * (supergroups, channels and secret chats always delete for everyone).
     */
    suspend fun delete(
        chatId: Long,
        messageIds: List<Long>,
        forEveryone: Boolean,
    ): Outcome<Unit>

    /**
     * Pins message [messageId] of chat [chatId], with a notification where the chat sends one (groups); for the user
     * alone if [onlyForSelf], which only private chats allow.
     */
    suspend fun pin(
        chatId: Long,
        messageId: Long,
        onlyForSelf: Boolean,
    ): Outcome<Unit>

    /** Unpins message [messageId] of chat [chatId]. */
    suspend fun unpin(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit>

    /**
     * Sends the failed messages [messageIds] of chat [chatId] again; each then shows as being sent, replacing the
     * failed one. Fails if TDLib cannot send one of them again (yet).
     */
    suspend fun resend(
        chatId: Long,
        messageIds: List<Long>,
    ): Outcome<Unit>
}
