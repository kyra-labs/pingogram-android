package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toReactionType
import dev.kyralabs.pingogram.core.data.mapper.toSticker
import dev.kyralabs.pingogram.core.data.mapper.toTdFormattedText
import dev.kyralabs.pingogram.core.data.mapper.toTdReactionType
import dev.kyralabs.pingogram.core.domain.model.AvailableReactions
import dev.kyralabs.pingogram.core.domain.model.ChatReportResult
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ForwardOptions
import dev.kyralabs.pingogram.core.domain.model.MessageLink
import dev.kyralabs.pingogram.core.domain.model.MessageProperties
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.ReportOption
import dev.kyralabs.pingogram.core.domain.model.ReportOptionId
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [MessageActionRepository] on TDLib's message functions. Each call is one request; what it changes reaches the
 * history through TDLib's updates (`updateMessageInteractionInfo`, `updateMessageContent`, `updateMessageEdited`,
 * `updateMessageIsPinned`, `updateDeleteMessages`, `updateMessageSendFailed`), which `TdlibMessageRepository` applies.
 * TDLib applies reactions, deletions and pins at once, before Telegram confirms them; edits once Telegram has them.
 * Forwarded messages reach the target chat's history as new messages being sent (`updateNewMessage`). Reports go to
 * Telegram's moderators (`reportChat`), one request per step the user takes. Message links are TDLib's
 * (`getMessageLink`), which asks Telegram for a private chat's.
 */
@Suppress("TooManyFunctions") // One function per message action of the interface, each a single TDLib request.
@AccountScoped
class TdlibMessageActionRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : MessageActionRepository {
        override suspend fun properties(
            chatId: Long,
            messageId: Long,
        ): Outcome<MessageProperties> =
            client.request(TdApi.GetMessageProperties(chatId, messageId)).map { properties ->
                MessageProperties(
                    canBeReplied = properties.canBeReplied,
                    canBeEdited = properties.canBeEdited,
                    canBePinned = properties.canBePinned,
                    canBeDeletedOnlyForSelf = properties.canBeDeletedOnlyForSelf,
                    canBeDeletedForAllUsers = properties.canBeDeletedForAllUsers,
                    canBeSaved = properties.canBeSaved,
                    canBeRepliedInAnotherChat = properties.canBeRepliedInAnotherChat,
                    canGetLink = properties.canGetLink,
                    canEditMedia = properties.canEditMedia,
                    canBeForwarded = properties.canBeForwarded,
                    canBeCopied = properties.canBeCopied,
                    canBeCopiedToSecretChat = properties.canBeCopiedToSecretChat,
                    canBeReported = properties.canReportChat,
                    canGetViewers = properties.canGetViewers,
                )
            }

        /**
         * `getMessageAvailableReactions` for rows of [REACTION_ROW_SIZE]. Reactions that need Telegram Premium are left
         * out (TDLib marks them for users without it), and so is the paid reaction (`addPendingPaidMessageReaction`,
         * not offered), along with types added by a newer TDLib. A reaction in more than one list stays in the first.
         */
        override suspend fun availableReactions(
            chatId: Long,
            messageId: Long,
        ): Outcome<AvailableReactions> =
            client.request(TdApi.GetMessageAvailableReactions(chatId, messageId, REACTION_ROW_SIZE)).map { available ->
                val offered = mutableSetOf<ReactionType>()

                fun Array<TdApi.AvailableReaction>.offerable() =
                    asSequence()
                        .filterNot { it.needsPremium }
                        .mapNotNull { it.type.toReactionType() }
                        .filter { it != ReactionType.Paid && offered.add(it) }
                        .toImmutableList()
                val top = available.topReactions.offerable()
                val recent = available.recentReactions.offerable()
                AvailableReactions(top = top, recent = recent, popular = available.popularReactions.offerable())
            }

        /** `getCustomEmojiStickers`, keyed by each sticker's custom emoji id. */
        override suspend fun customEmojiStickers(customEmojiIds: List<Long>): Outcome<ImmutableMap<Long, Sticker>> {
            if (customEmojiIds.isEmpty()) return Outcome.Success(persistentMapOf())
            val ids = customEmojiIds.distinct().toLongArray()
            return client.request(TdApi.GetCustomEmojiStickers(ids)).map { stickers ->
                stickers.stickers
                    .mapNotNull { sticker ->
                        val id = (sticker.fullType as? TdApi.StickerFullTypeCustomEmoji)?.customEmojiId
                        val mapped = sticker.toSticker()
                        if (id == null || mapped == null) null else id to mapped
                    }.toMap()
                    .toImmutableMap()
            }
        }

        override suspend fun addReaction(
            chatId: Long,
            messageId: Long,
            reaction: ReactionType,
        ): Outcome<Unit> {
            val type = reaction.toTdReactionType() ?: return Outcome.Failure(PAID_REACTION)
            return client
                .request(TdApi.AddMessageReaction(chatId, messageId, type, false, true))
                .map {}
        }

        override suspend fun removeReaction(
            chatId: Long,
            messageId: Long,
            reaction: ReactionType,
        ): Outcome<Unit> {
            val type = reaction.toTdReactionType() ?: return Outcome.Failure(PAID_REACTION)
            return client.request(TdApi.RemoveMessageReaction(chatId, messageId, type)).map {}
        }

        /** `editMessageText` with an `inputMessageText` and TDLib's default link preview; nothing to do with drafts. */
        override suspend fun editText(
            chatId: Long,
            messageId: Long,
            text: FormattedText,
        ): Outcome<Unit> {
            val content = TdApi.InputMessageText(text.toTdFormattedText(), null, false)
            return client.request(TdApi.EditMessageText(chatId, messageId, null, content)).map {}
        }

        override suspend fun editCaption(
            chatId: Long,
            messageId: Long,
            caption: FormattedText,
            showCaptionAboveMedia: Boolean,
        ): Outcome<Unit> =
            client
                .request(
                    TdApi.EditMessageCaption(
                        chatId,
                        messageId,
                        null,
                        caption.toTdFormattedText(),
                        showCaptionAboveMedia,
                    ),
                ).map {}

        override suspend fun delete(
            chatId: Long,
            messageIds: List<Long>,
            forEveryone: Boolean,
        ): Outcome<Unit> = client.request(TdApi.DeleteMessages(chatId, messageIds.toLongArray(), forEveryone)).map {}

        /** `pinChatMessage` with its notification on: TDLib and Telegram turn it off where chats send none. */
        override suspend fun pin(
            chatId: Long,
            messageId: Long,
            onlyForSelf: Boolean,
        ): Outcome<Unit> = client.request(TdApi.PinChatMessage(chatId, messageId, false, onlyForSelf)).map {}

        override suspend fun unpin(
            chatId: Long,
            messageId: Long,
        ): Outcome<Unit> = client.request(TdApi.UnpinChatMessage(chatId, messageId)).map {}

        /**
         * `resendMessages` of [messageIds] in increasing order, as td_api.tl requires, without a new quote or Telegram
         * Stars. TDLib answers with null for a message it cannot send again (not retriable, or too early), which fails
         * the call.
         */
        override suspend fun resend(
            chatId: Long,
            messageIds: List<Long>,
        ): Outcome<Unit> {
            val ids = messageIds.distinct().sorted().toLongArray()
            return when (val result = client.request(TdApi.ResendMessages(chatId, ids, null, 0))) {
                is Outcome.Failure -> {
                    result
                }

                is Outcome.Success -> {
                    if (result.value.messages.any { it == null }) {
                        NOT_RESENT
                    } else {
                        Outcome.Success(
                            Unit,
                        )
                    }
                }
            }
        }

        /**
         * The comment as `sendMessage` of an `inputMessageText` that keeps the target chat's draft (`clear_draft`
         * false: the user wrote the comment in the forward sheet, not in that chat), then `forwardMessages` of the
         * messages in increasing order, as td_api.tl requires, to the chat itself (no topic), sent now with a sound.
         * TDLib answers with null for each message it does not forward.
         */
        override suspend fun forward(
            fromChatId: Long,
            messageIds: List<Long>,
            toChatId: Long,
            comment: FormattedText?,
            options: ForwardOptions,
        ): Outcome<Unit> {
            if (comment != null) {
                val content = TdApi.InputMessageText(comment.toTdFormattedText(), null, false)
                val sent = client.request(TdApi.SendMessage(toChatId, null, null, NOW, null, content))
                if (sent is Outcome.Failure) return sent
            }
            val ids = messageIds.distinct().sorted().toLongArray()
            val request =
                TdApi.ForwardMessages(
                    toChatId,
                    null,
                    fromChatId,
                    ids,
                    NOW,
                    options.hideSenderName,
                    options.hideSenderName && options.hideCaptions,
                )
            return when (val result = client.request(request)) {
                is Outcome.Failure -> {
                    result
                }

                is Outcome.Success -> {
                    if (result.value.messages.any { it == null }) {
                        NOT_FORWARDED
                    } else {
                        Outcome.Success(
                            Unit,
                        )
                    }
                }
            }
        }

        override suspend fun report(
            chatId: Long,
            messageIds: List<Long>,
            option: ReportOptionId,
            text: String,
        ): Outcome<ChatReportResult> =
            client
                .request(TdApi.ReportChat(chatId, option.bytes, messageIds.toLongArray(), text))
                .map { it.toChatReportResult() }

        /** `getMessageLink` without a media timestamp, checklist task or poll option. */
        override suspend fun messageLink(
            chatId: Long,
            messageId: Long,
            forAlbum: Boolean,
            inThread: Boolean,
        ): Outcome<MessageLink> =
            client
                .request(TdApi.GetMessageLink(chatId, messageId, 0, 0, "", forAlbum, inThread))
                .map { MessageLink(it.link, it.isPublic) }

        private companion object {
            /** Sent at once, with a notification: the forward sheet offers no options for that. */
            val NOW: TdApi.MessageSendOptions get() = SendOptions().toTdOptions()

            /** A message TDLib did not forward: its content is not allowed in the target chat. */
            val NOT_FORWARDED = Outcome.Failure(DomainError.SendingNotAllowed)

            /** Reactions per row of TDLib's reaction picker; it offers up to two rows of top reactions. */
            const val REACTION_ROW_SIZE = 8

            /** The paid reaction is neither added nor removed this way (td_api.tl `addMessageReaction`). */
            val PAID_REACTION = DomainError.Unknown(400, "The paid reaction can't be changed here")

            val NOT_RESENT = Outcome.Failure(DomainError.Unknown(400, "The message can't be sent again"))
        }
    }

/** What TDLib's answer to `reportChat` says; [ChatReportResult.MessagesRequired] for one a newer TDLib adds too. */
private fun TdApi.ReportChatResult.toChatReportResult(): ChatReportResult =
    when (this) {
        is TdApi.ReportChatResultOk -> {
            ChatReportResult.Reported
        }

        is TdApi.ReportChatResultOptionRequired -> {
            ChatReportResult.OptionRequired(
                title = title.orEmpty(),
                options = options.map { ReportOption(ReportOptionId(it.id), it.text.orEmpty()) }.toImmutableList(),
            )
        }

        is TdApi.ReportChatResultTextRequired -> {
            ChatReportResult.TextRequired(ReportOptionId(optionId), isOptional)
        }

        else -> {
            ChatReportResult.MessagesRequired
        }
    }
