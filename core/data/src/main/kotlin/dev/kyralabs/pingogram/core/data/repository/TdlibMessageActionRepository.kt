package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toReactionType
import dev.kyralabs.pingogram.core.data.mapper.toTdFormattedText
import dev.kyralabs.pingogram.core.domain.model.AvailableReactions
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageProperties
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MessageActionRepository] on TDLib's message functions. Each call is one request; what it changes reaches the
 * history through TDLib's updates (`updateMessageInteractionInfo`, `updateMessageContent`, `updateMessageEdited`,
 * `updateMessageIsPinned`, `updateDeleteMessages`, `updateMessageSendFailed`), which `TdlibMessageRepository` applies.
 * TDLib applies reactions, deletions and pins at once, before Telegram confirms them; edits once Telegram has them.
 */
@Singleton
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
                )
            }

        /**
         * `getMessageAvailableReactions` for rows of [REACTION_ROW_SIZE]. Reactions that need Telegram Premium are left
         * out (TDLib marks them for users without it), and so is the paid reaction (`addPendingPaidMessageReaction`,
         * not offered), along with types added by a newer TDLib.
         */
        override suspend fun availableReactions(
            chatId: Long,
            messageId: Long,
        ): Outcome<AvailableReactions> =
            client.request(TdApi.GetMessageAvailableReactions(chatId, messageId, REACTION_ROW_SIZE)).map { available ->
                val top =
                    available.topReactions
                        .filterNot { it.needsPremium }
                        .mapNotNull { it.type.toReactionType() }
                        .filterNot { it == ReactionType.Paid }
                AvailableReactions(top.toImmutableList())
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

        private companion object {
            /** Reactions per row of TDLib's reaction picker; it offers up to two rows of top reactions. */
            const val REACTION_ROW_SIZE = 8

            /** The paid reaction is neither added nor removed this way (td_api.tl `addMessageReaction`). */
            val PAID_REACTION = DomainError.Unknown(400, "The paid reaction can't be changed here")

            val NOT_RESENT = Outcome.Failure(DomainError.Unknown(400, "The message can't be sent again"))
        }
    }

/** Null for the paid reaction, which has its own functions. */
private fun ReactionType.toTdReactionType(): TdApi.ReactionType? =
    when (this) {
        is ReactionType.Emoji -> TdApi.ReactionTypeEmoji(emoji)
        is ReactionType.CustomEmoji -> TdApi.ReactionTypeCustomEmoji(customEmojiId)
        ReactionType.Paid -> null
    }
