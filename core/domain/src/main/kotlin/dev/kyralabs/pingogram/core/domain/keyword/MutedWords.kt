package dev.kyralabs.pingogram.core.domain.keyword

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MutedWordSettings

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
     * not the user's own, and not one that mentions the user (Telegram counts a reply to the user as one), in its text
     * or caption.
     */
    fun mutes(
        chat: Chat,
        message: Message,
    ): Boolean {
        val matcher = matcher ?: return false
        val type = chat.type
        val isGroup = type is ChatType.BasicGroup || (type is ChatType.Supergroup && !type.isChannel)
        if (!isGroup || message.isOutgoing || message.containsUnreadMention) return false
        return message.content.mutableText()?.let(matcher::firstMatch) != null
    }
}

/** The text muted words look in: a text message's text, or a caption. */
private fun MessageContent.mutableText(): String? =
    when (this) {
        is MessageContent.Text -> text.text
        is MessageContent.Photo -> caption.text
        is MessageContent.Video -> caption.text
        is MessageContent.Animation -> caption.text
        is MessageContent.VoiceNote -> caption.text
        is MessageContent.Audio -> caption.text
        is MessageContent.Document -> caption.text
        is MessageContent.PaidMedia -> caption.text
        else -> null
    }
