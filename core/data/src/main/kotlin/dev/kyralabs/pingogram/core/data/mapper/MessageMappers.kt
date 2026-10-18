package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.data.error.toDomainError
import dev.kyralabs.pingogram.core.domain.model.ForwardInfo
import dev.kyralabs.pingogram.core.domain.model.InteractionInfo
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageOrigin
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Reaction
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.ReplyTo
import dev.kyralabs.pingogram.core.domain.model.SelfDestruct
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.tdlib.TdError
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import kotlin.time.Duration.Companion.seconds

/**
 * The [Message] for a TDLib message: the one place where TDLib messages become domain messages, for the chat list
 * preview and the conversation alike. Nothing is dropped: content this version does not show becomes
 * `MessageContent.Unsupported`, service messages it does not describe `ServiceAction.Other`.
 */
internal fun TdApi.Message.toMessage(): Message {
    // A sender type added by a newer TDLib: the message is attributed to the chat.
    val sender = senderId.toMessageSender() ?: MessageSender.Chat(chatId)
    return Message(
        id = id,
        chatId = chatId,
        sender = sender,
        date = date.toLong(),
        editDate = editDate.toLong(),
        isOutgoing = isOutgoing,
        sendingState = sendingState?.toSendingState(),
        isPinned = isPinned,
        isChannelPost = isChannelPost,
        containsUnreadMention = containsUnreadMention,
        mediaAlbumId = mediaAlbumId,
        authorSignature = authorSignature,
        viaBotUserId = viaBotUserId,
        replyTo = replyTo?.toReplyTo(),
        forwardInfo = forwardInfo?.toForwardInfo(),
        interactionInfo = interactionInfo?.toInteractionInfo(),
        selfDestruct = selfDestructType?.toSelfDestruct(),
        content = content.toMessageContent(sender, isChannelPost),
    )
}

/** Null for a sender type added by a newer TDLib. */
internal fun TdApi.MessageSender.toMessageSender(): MessageSender? =
    when (this) {
        is TdApi.MessageSenderUser -> MessageSender.User(userId)
        is TdApi.MessageSenderChat -> MessageSender.Chat(chatId)
        else -> null
    }

private fun TdApi.MessageSendingState.toSendingState(): SendingState =
    when (this) {
        is TdApi.MessageSendingStateFailed -> {
            SendingState.Failed(
                error = TdError(error.code, error.message).toDomainError(),
                canRetry = canRetry,
                retryAfter = retryAfter.takeIf { it > 0 }?.seconds,
            )
        }

        // Pending, and a state added by a newer TDLib: not sent yet.
        else -> {
            SendingState.Pending
        }
    }

private fun TdApi.MessageReplyTo.toReplyTo(): ReplyTo? =
    when (this) {
        is TdApi.MessageReplyToMessage -> {
            ReplyTo.Message(
                chatId = chatId,
                messageId = messageId,
                quote = quote?.text?.toFormattedText(),
                origin = origin?.toMessageOrigin(),
                originDate = originSendDate.toLong(),
                // Only media of the replied message, never a service message: there is no sender to describe.
                content = content?.toMessageContent(sender = null, isChannelPost = false),
            )
        }

        is TdApi.MessageReplyToStory -> {
            ReplyTo.Story(storyPosterChatId, storyId)
        }

        // A reply type added by a newer TDLib: shown as no reply.
        else -> {
            null
        }
    }

private fun TdApi.MessageForwardInfo.toForwardInfo() = ForwardInfo(origin.toMessageOrigin(), date.toLong())

private fun TdApi.MessageOrigin.toMessageOrigin(): MessageOrigin =
    when (this) {
        is TdApi.MessageOriginUser -> MessageOrigin.User(senderUserId)

        is TdApi.MessageOriginHiddenUser -> MessageOrigin.HiddenUser(senderName)

        is TdApi.MessageOriginChat -> MessageOrigin.Chat(senderChatId, authorSignature)

        is TdApi.MessageOriginChannel -> MessageOrigin.Channel(chatId, messageId, authorSignature)

        // An origin added by a newer TDLib: an unknown sender.
        else -> MessageOrigin.HiddenUser(name = "")
    }

private fun TdApi.MessageInteractionInfo.toInteractionInfo() =
    InteractionInfo(
        viewCount = viewCount,
        forwardCount = forwardCount,
        replyCount = replyInfo?.replyCount,
        // A reaction type added by a newer TDLib cannot be shown; the others still are.
        reactions =
            reactions
                ?.reactions
                .orEmpty()
                .mapNotNull { reaction ->
                    reaction.type.toReactionType()?.let { Reaction(it, reaction.totalCount, reaction.isChosen) }
                }.toImmutableList(),
    )

/** Null for a reaction type added by a newer TDLib. */
internal fun TdApi.ReactionType.toReactionType(): ReactionType? =
    when (this) {
        is TdApi.ReactionTypeEmoji -> ReactionType.Emoji(emoji)
        is TdApi.ReactionTypeCustomEmoji -> ReactionType.CustomEmoji(customEmojiId)
        is TdApi.ReactionTypePaid -> ReactionType.Paid
        else -> null
    }

private fun TdApi.MessageSelfDestructType.toSelfDestruct(): SelfDestruct =
    when (this) {
        is TdApi.MessageSelfDestructTypeTimer -> SelfDestruct.Timer(selfDestructTime)

        // Immediately, and a type added by a newer TDLib: the strictest reading.
        else -> SelfDestruct.Immediately
    }
