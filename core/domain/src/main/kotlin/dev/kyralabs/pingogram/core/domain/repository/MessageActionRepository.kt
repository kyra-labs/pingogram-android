package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AvailableReactions
import dev.kyralabs.pingogram.core.domain.model.ChatReportResult
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ForwardOptions
import dev.kyralabs.pingogram.core.domain.model.MessageLink
import dev.kyralabs.pingogram.core.domain.model.MessageProperties
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.ReportOptionId
import dev.kyralabs.pingogram.core.domain.model.Sticker
import kotlinx.collections.immutable.ImmutableMap

/**
 * What the user does to a message of the conversation (`REQUIREMENTS.md` FR-C6, FR-C7): what TDLib allows for it, its
 * reactions, editing, deleting, pinning, forwarding, sending it again and reporting it. Nothing is kept here: the
 * changes reach the message through TDLib's updates, which `MessageRepository.history` applies.
 *
 * Every call fails with a `DomainError`: `FloodWait`, `Network` for a request that gets no answer in time, `NotFound`
 * for a message or chat TDLib does not know, or another one when TDLib refuses it.
 */
@Suppress("TooManyFunctions") // One function per message action, and the lookups the actions sheet needs.
interface MessageActionRepository {
    /** What the user can do with message [messageId] of chat [chatId] right now; TDLib answers from memory. */
    suspend fun properties(
        chatId: Long,
        messageId: Long,
    ): Outcome<MessageProperties>

    /**
     * The reactions the user can add to message [messageId] of chat [chatId], for the quick reactions and the reactions
     * picker; none where reactions are off.
     */
    suspend fun availableReactions(
        chatId: Long,
        messageId: Long,
    ): Outcome<AvailableReactions>

    /**
     * The stickers of the custom emoji [customEmojiIds], by custom emoji id, which picture custom emoji reactions;
     * those TDLib does not know, or in a format it added later, are left out. TDLib answers from memory or its
     * database, else asks Telegram.
     */
    suspend fun customEmojiStickers(customEmojiIds: List<Long>): Outcome<ImmutableMap<Long, Sticker>>

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

    /**
     * Forwards [messageIds] of chat [fromChatId] to chat [toChatId] as [options] say, in the order they were sent (an
     * album stays an album), after [comment] as a message of its own if it is not null; the comment is sent as a new
     * message is, but leaves the draft of [toChatId] alone. Each message must be one TDLib lets be forwarded
     * (`MessageProperties.canBeForwarded`); at most [MAX_FORWARDED] at once.
     *
     * Succeeds once TDLib accepted every message, which it then puts into the history of [toChatId] as being sent.
     * Fails with the comment's failure, without forwarding anything, or with `DomainError.SendingNotAllowed` when
     * TDLib does not forward every message (e.g. a photo to a group where members may send no photos).
     */
    suspend fun forward(
        fromChatId: Long,
        messageIds: List<Long>,
        toChatId: Long,
        comment: FormattedText?,
        options: ForwardOptions,
    ): Outcome<Unit>

    /**
     * Reports [messageIds] of chat [chatId] to Telegram's moderators: first with [ReportOptionId.Initial], then with
     * the option the user chose among those TDLib asked for ([ChatReportResult.OptionRequired]), and with the user's
     * [text] where TDLib asked for one ([ChatReportResult.TextRequired]). Each message must be one TDLib lets be
     * reported (`MessageProperties.canBeReported`). One call per step the user takes: never repeated on its own.
     */
    suspend fun report(
        chatId: Long,
        messageIds: List<Long>,
        option: ReportOptionId,
        text: String = "",
    ): Outcome<ChatReportResult>

    /**
     * The link to message [messageId] of chat [chatId], a supergroup's or a channel's (td_api.tl `getMessageLink`;
     * `MessageProperties.canGetLink`): to its whole album if [forAlbum], and to it in its forum topic or message
     * thread, or as a channel post's comment, if [inThread]. A private chat's link opens only for its members.
     */
    suspend fun messageLink(
        chatId: Long,
        messageId: Long,
        forAlbum: Boolean,
        inThread: Boolean,
    ): Outcome<MessageLink>

    companion object {
        /** How many messages [forward] takes at once (td_api.tl `forwardMessages.message_ids`). */
        const val MAX_FORWARDED = 100
    }
}
