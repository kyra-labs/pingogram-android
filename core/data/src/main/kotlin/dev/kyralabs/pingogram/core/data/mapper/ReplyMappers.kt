package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.domain.model.TextQuote
import org.drinkless.tdlib.TdApi

/**
 * What [this] replies to, as TDLib takes it for a new message or a draft: `inputMessageReplyToMessage` for a message
 * of the same chat, `inputMessageReplyToExternalMessage` for one of another chat, each with its quote if it quotes
 * one; no checklist task, no poll option.
 */
internal fun ReplyTarget.toInputMessageReplyTo(): TdApi.InputMessageReplyTo {
    val tdQuote = quote?.let { TdApi.InputTextQuote(it.text.toTdFormattedText(), it.position) }
    val otherChat = chatId
    return if (otherChat == null) {
        TdApi.InputMessageReplyToMessage(messageId, tdQuote, 0, "")
    } else {
        TdApi.InputMessageReplyToExternalMessage(otherChat, messageId, tdQuote, 0, "")
    }
}

/** The reply of a draft, to a message of its chat or of another one; null for a story or a reply added later. */
internal fun TdApi.InputMessageReplyTo.toReplyTarget(): ReplyTarget? =
    when (this) {
        is TdApi.InputMessageReplyToMessage -> ReplyTarget(messageId, quote?.toTextQuote())
        is TdApi.InputMessageReplyToExternalMessage -> ReplyTarget(messageId, quote?.toTextQuote(), chatId)
        else -> null
    }

private fun TdApi.InputTextQuote.toTextQuote(): TextQuote = TextQuote(text.toFormattedText(), position)
