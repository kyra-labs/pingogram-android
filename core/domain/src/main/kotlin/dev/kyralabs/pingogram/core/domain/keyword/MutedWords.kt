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
        val text = message.content.mutableText()
        return chat.isGroup &&
            !message.isOutgoing &&
            !message.containsUnreadMention &&
            text != null &&
            !text.mentionsUser(me?.id, me?.usernames.orEmpty()) &&
            matcher?.firstMatch(text.text) != null
    }

    /**
     * Whether a push-only message of [chat], of which TDLib keeps nothing but [text], has a muted word: in a group,
     * not the user's own ([isOutgoing]), and never one of a notification group of mentions ([mentionsMe]: TDLib files
     * a mention of the user, or a reply to them, there).
     */
    fun mutesPush(
        chat: Chat,
        text: String,
        isOutgoing: Boolean,
        mentionsMe: Boolean,
    ): Boolean = chat.isGroup && !isOutgoing && !mentionsMe && matcher?.firstMatch(text) != null

    /** A basic group, or a supergroup that is no channel. */
    private val Chat.isGroup: Boolean
        get() = type.let { it is ChatType.BasicGroup || (it is ChatType.Supergroup && !it.isChannel) }
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
