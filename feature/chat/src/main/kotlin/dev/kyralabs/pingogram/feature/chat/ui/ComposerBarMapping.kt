package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.text.shortName
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerMode
import dev.kyralabs.pingogram.feature.chat.ui.composer.EditTarget
import dev.kyralabs.pingogram.feature.chat.ui.composer.editTarget
import dev.kyralabs.pingogram.feature.chat.R as ChatR

/**
 * The composer's bar for [mode] in [chat] (`REQUIREMENTS.md` FR-C5, FR-C6): the message it replies to or edits, from
 * the [history] or as fetched for the bar ([targets]), with one line of it, and for a reply its sender ("You" for the
 * user's own messages, as reply quotes say). While the message is on its way the bar says it loads. Null for a new
 * message.
 */
internal fun composerBar(
    mode: ComposerMode,
    chat: Chat,
    history: ChatHistory,
    targets: Map<Long, Message?>,
    people: ChatPeople,
): ComposerBar? {
    val messageId = mode.messageId ?: return null
    val message = history.messages.findById(messageId) ?: targets[messageId]
    val preview =
        message?.let { messagePreviewText(it, people.myUserId) { sender -> shortNameOf(sender, chat, people) } }
            ?: UiText.resource(ChatR.string.chat_reply_loading)
    return when (mode) {
        is ComposerMode.Replying -> {
            ComposerBar.Reply(
                messageId = messageId,
                sender = message?.let { senderNameOf(it, chat, people) },
                preview = preview,
            )
        }

        is ComposerMode.Editing -> {
            ComposerBar.Edit(messageId, preview, canBeEmpty = message?.editTarget is EditTarget.Caption)
        }

        ComposerMode.Writing -> {
            null
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
