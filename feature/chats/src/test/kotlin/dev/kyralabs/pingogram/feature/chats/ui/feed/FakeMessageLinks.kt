package dev.kyralabs.pingogram.feature.chats.ui.feed

import dev.kyralabs.pingogram.core.common.result.DomainError
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
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import kotlinx.collections.immutable.ImmutableMap

/**
 * The message actions the feed uses: only links, "https://t.me/c/<chat>/<message>" or [linkFailure]. Every other
 * action fails, as the feed never asks for it.
 */
class FakeMessageLinks : MessageActionRepository {
    var linkFailure: DomainError? = null

    override suspend fun messageLink(
        chatId: Long,
        messageId: Long,
        forAlbum: Boolean,
        inThread: Boolean,
    ): Outcome<MessageLink> =
        linkFailure?.let { Outcome.Failure(it) }
            ?: Outcome.Success(MessageLink("https://t.me/c/$chatId/$messageId", false))

    override suspend fun properties(
        chatId: Long,
        messageId: Long,
    ): Outcome<MessageProperties> = unused()

    override suspend fun availableReactions(
        chatId: Long,
        messageId: Long,
    ): Outcome<AvailableReactions> = unused()

    override suspend fun customEmojiStickers(customEmojiIds: List<Long>): Outcome<ImmutableMap<Long, Sticker>> =
        unused()

    override suspend fun addReaction(
        chatId: Long,
        messageId: Long,
        reaction: ReactionType,
    ): Outcome<Unit> = unused()

    override suspend fun removeReaction(
        chatId: Long,
        messageId: Long,
        reaction: ReactionType,
    ): Outcome<Unit> = unused()

    override suspend fun editText(
        chatId: Long,
        messageId: Long,
        text: FormattedText,
    ): Outcome<Unit> = unused()

    override suspend fun editCaption(
        chatId: Long,
        messageId: Long,
        caption: FormattedText,
        showCaptionAboveMedia: Boolean,
    ): Outcome<Unit> = unused()

    override suspend fun delete(
        chatId: Long,
        messageIds: List<Long>,
        forEveryone: Boolean,
    ): Outcome<Unit> = unused()

    override suspend fun pin(
        chatId: Long,
        messageId: Long,
        onlyForSelf: Boolean,
    ): Outcome<Unit> = unused()

    override suspend fun unpin(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit> = unused()

    override suspend fun resend(
        chatId: Long,
        messageIds: List<Long>,
    ): Outcome<Unit> = unused()

    override suspend fun forward(
        fromChatId: Long,
        messageIds: List<Long>,
        toChatId: Long,
        comment: FormattedText?,
        options: ForwardOptions,
    ): Outcome<Unit> = unused()

    override suspend fun report(
        chatId: Long,
        messageIds: List<Long>,
        option: ReportOptionId,
        text: String,
    ): Outcome<ChatReportResult> = unused()

    private fun <T> unused(): Outcome<T> =
        Outcome.Failure(DomainError.Unknown(code = 0, message = "Not used by the feed"))
}
