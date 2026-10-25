package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.text.previewOf
import dev.kyralabs.pingogram.core.ui.text.shortName
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerMode
import dev.kyralabs.pingogram.feature.chat.ui.composer.EditTarget
import dev.kyralabs.pingogram.feature.chat.ui.composer.editTarget
import dev.kyralabs.pingogram.feature.chat.R as ChatR

/**
 * The composer's bar for [mode] in [chat] (`REQUIREMENTS.md` FR-C5, FR-C6): the message it replies to or edits, from
 * the [history] or as fetched for the bar ([targets]; [elsewhere] for a message of another chat), with one line of it
 * (the quoted part of a quote reply), and for a reply its sender ("You" for the user's own messages, as reply quotes
 * say), and the other chat's title for a reply in another chat. While the message is on its way the bar says it
 * loads. Null for a new message.
 */
internal fun composerBar(
    mode: ComposerMode,
    chat: Chat,
    history: ChatHistory,
    targets: Map<Long, Message?>,
    people: ChatPeople,
    elsewhere: Message? = null,
): ComposerBar? {
    val messageId = mode.messageId ?: return null
    val otherChatId = mode.replyTo?.chatId
    val message =
        if (otherChatId != null) {
            elsewhere?.takeIf { it.chatId == otherChatId && it.id == messageId }
        } else {
            history.messages.findById(messageId) ?: targets[messageId]
        }
    val preview =
        message?.let { messagePreviewText(it, people.myUserId) { sender -> shortNameOf(sender, chat, people) } }
            ?: UiText.resource(ChatR.string.chat_reply_loading)
    return when (mode) {
        is ComposerMode.Replying -> {
            val quote = mode.target.quote
            ComposerBar.Reply(
                messageId = messageId,
                sender =
                    if (otherChatId == null) {
                        message?.let { senderNameOf(it, chat, people) }
                    } else {
                        elsewhereTitle(message, otherChatId, chat, people)
                    },
                preview = quote?.let { previewOf(it.text) } ?: preview,
                isQuote = quote != null,
                isElsewhere = otherChatId != null,
            )
        }

        is ComposerMode.Editing -> {
            ComposerBar.Edit(
                messageId = messageId,
                preview = preview,
                canBeEmpty = message?.editTarget is EditTarget.Caption,
                replace = mode.replace,
            )
        }

        ComposerMode.Writing -> {
            null
        }
    }
}

/**
 * "Reply to Ada in Team chat" for [message] of chat [otherChatId] ("Reply to your message in …" for the user's own);
 * "Reply in Team chat" while the message loads; null while the other chat is not known either.
 */
private fun elsewhereTitle(
    message: Message?,
    otherChatId: Long,
    chat: Chat,
    people: ChatPeople,
): UiText? {
    val title = people.chats[otherChatId]?.title?.bidiIsolated()
    return when {
        title == null -> {
            null
        }

        message == null -> {
            UiText.resource(ChatR.string.composer_reply_in, title)
        }

        message.isOutgoing -> {
            UiText.resource(ChatR.string.composer_reply_elsewhere_own, title)
        }

        else -> {
            val sender = senderNameOf(message, chat, people).bidiIsolated()
            UiText.resource(ChatR.string.composer_reply_elsewhere, sender, title)
        }
    }
}

/** "You" for the user's own message; otherwise its sender's full name, or the chat's title. */
internal fun senderNameOf(
    message: Message,
    chat: Chat,
    people: ChatPeople,
): UiText =
    if (message.isOutgoing) {
        UiText.resource(R.string.preview_sender_you)
    } else {
        fullNameOf(refOf(message.sender, chat, people))
    }

/** What a preview calls [sender]: a user's short name or a chat's title; null ("Someone") if not known. */
internal fun shortNameOf(
    sender: MessageSender,
    chat: Chat,
    people: ChatPeople,
): UiText? =
    when (val ref = refOf(sender, chat, people)) {
        is User -> ref.shortName()
        is ChatRef -> UiText.Plain(ref.title)
        else -> null
    }

/** The looked-up user or chat [sender] is; null if not known. */
private fun refOf(
    sender: MessageSender,
    chat: Chat,
    people: ChatPeople,
): Any? =
    when (sender) {
        is MessageSender.User -> {
            people.users[sender.userId]
        }

        is MessageSender.Chat -> {
            if (sender.chatId == chat.id) ChatRef(chat.id, chat.title, chat.photo) else people.chats[sender.chatId]
        }
    }
