package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ReplyTo
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.contentKindText
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.text.previewOf
import dev.kyralabs.pingogram.feature.chat.R as ChatR

/**
 * The quote of the replied message ([reply]): its sender and preview (the quoted part, if the sender quoted one), from
 * the loaded or fetched message; the origin and content TDLib bundled for a message of another chat while there is
 * none; otherwise what keeps it from showing: loading, deleted, unavailable. A reply to a story quotes the story.
 * A replied message of this chat opens from the quote, even one that is gone: tapping it then says so.
 */
internal fun ItemInputs.toQuote(reply: ReplyInputs): ReplyQuote {
    val replyTo =
        reply.replyTo as? ReplyTo.Message ?: return ReplyQuote(null, null, UiText.resource(R.string.preview_story))
    val replied = reply.replied ?: (reply.state as? ReplyState.Loaded)?.message
    val origin = replyTo.origin
    val opens = replyTo.messageId.takeIf { it != 0L && replyTo.chatId == message.chatId }
    return when {
        replied != null -> {
            val you = UiText.resource(R.string.preview_sender_you)
            val sender = if (replied.isOutgoing) you else fullNameOf(reply.repliedSender)
            val text = replyTo.quote?.let(::previewOf) ?: messagePreviewText(replied, myUserId, ::shortNameOf)
            ReplyQuote(sender, replied.sender.id, text, opens)
        }

        origin != null -> {
            val text =
                replyTo.quote?.let(::previewOf)
                    ?: replyTo.content?.let(::contentKindText)
                    ?: UiText.resource(ChatR.string.chat_reply_message)
            ReplyQuote(originName(origin, reply.origin), origin.id, text, opens)
        }

        else -> {
            val text =
                when ((reply.state as? ReplyState.Failed)?.error) {
                    null -> ChatR.string.chat_reply_loading
                    DomainError.NotFound -> ChatR.string.chat_reply_deleted
                    else -> ChatR.string.chat_reply_unavailable
                }
            ReplyQuote(null, null, UiText.resource(text), opens)
        }
    }
}
