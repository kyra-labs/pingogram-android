package dev.kyralabs.pingogram.core.domain.model

/**
 * What a message being sent, or a draft, replies to (`REQUIREMENTS.md` FR-C5, FR-C6): message [messageId] of the chat
 * it goes to, or, where [chatId] is not null, of chat [chatId] ("Reply in another chat", td_api.tl
 * `inputMessageReplyToExternalMessage`). [quote] is the part of the replied message's text the reply quotes (a quote
 * reply); null replies to the whole message.
 */
data class ReplyTarget(
    val messageId: Long,
    val quote: TextQuote? = null,
    val chatId: Long? = null,
) {
    override fun toString() = "ReplyTarget(messageId=$messageId, chatId=$chatId, quote=${quote != null})"
}

/**
 * The part of a message's text a reply quotes (td_api.tl `inputTextQuote`): [text], with the formatting Telegram keeps
 * in quotes, found at [position] (UTF-16 code units, as `String` indices) of the replied message's text or caption.
 */
data class TextQuote(
    val text: FormattedText,
    val position: Int,
)

/**
 * The link to a message (td_api.tl `messageLink`): [link] opens it; only members of the chat can open a link that is
 * not [isPublic] (a private group's or channel's).
 */
data class MessageLink(
    val link: String,
    val isPublic: Boolean,
) {
    override fun toString() = "MessageLink(isPublic=$isPublic)"
}
