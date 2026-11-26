package dev.kyralabs.pingogram.core.domain.keyword

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MutedWordSettings
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.mentionsUser

/**
 * Muted words (`REQUIREMENTS.md` FR-X21) where a message's text shows outside its conversation, the chat list's
 * preview and notifications, which then say a message has a muted word instead. Made once per [settings]; nothing is
 * muted while they are off or have no words.
 */
class MutedWords(
    settings: MutedWordSettings,
) {
    private val matcher =
        settings
            .takeIf { it.enabled && it.words.isNotEmpty() }
            ?.let { KeywordMatcher(it.words, withinUnspacedScripts = true) }

    /**
     * Whether [message] of [chat] has a muted word: in a group (a basic group, or a supergroup that is no channel),
     * not the user's own, and never one that mentions the user ([me]), read or not: TDLib's unread mention (which
     * counts a reply to the user), or a mention by name or username in its text. Looked for in its text or caption.
     */
    fun mutes(
        chat: Chat,
        message: Message,
        me: User?,
    ): Boolean {
        val type = chat.type
        val isGroup = type is ChatType.BasicGroup || (type is ChatType.Supergroup && !type.isChannel)
        val text = message.content.mutableText()
        return isGroup &&
            !message.isOutgoing &&
            !message.containsUnreadMention &&
            text != null &&
            !text.mentionsUser(me?.id, me?.usernames.orEmpty()) &&
            matcher?.firstMatch(text.text) != null
    }
}

/** The text muted words look in: a text message's text, or a caption. */
private fun MessageContent.mutableText(): FormattedText? =
    when (this) {
        is MessageContent.Text -> text
        is MessageContent.Photo -> caption
        is MessageContent.Video -> caption
        is MessageContent.Animation -> caption
        is MessageContent.VoiceNote -> caption
        is MessageContent.Audio -> caption
        is MessageContent.Document -> caption
        is MessageContent.PaidMedia -> caption
        else -> null
    }
