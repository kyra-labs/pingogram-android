package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.MessageOrigin

/**
 * What "Forwarded from …" opens: the sender's profile; the chat, at the original post of a channel; nothing for a user
 * who hides their account.
 */
internal fun MessageOrigin.toForwardSource(): ForwardSource =
    when (this) {
        is MessageOrigin.User -> ForwardSource.Profile(userId)
        is MessageOrigin.HiddenUser -> ForwardSource.Hidden
        is MessageOrigin.Chat -> ForwardSource.Chat(chatId, messageId = null)
        is MessageOrigin.Channel -> ForwardSource.Chat(chatId, messageId.takeIf { it != 0L })
    }

/**
 * Opens this source from the conversation of chat [chatId]: the sender's profile, or the chat at the original message,
 * through [onEffect]; a message of [chatId] itself in place, through [showMessage]; for a user who hides their
 * account, a snackbar ([ChatEffect.ForwardSourceHidden]).
 */
internal fun ForwardSource.open(
    chatId: Long,
    showMessage: (messageId: Long) -> Unit,
    onEffect: (ChatEffect) -> Unit,
) {
    when (this) {
        is ForwardSource.Profile -> {
            onEffect(ChatEffect.OpenProfile(userId))
        }

        ForwardSource.Hidden -> {
            onEffect(ChatEffect.ForwardSourceHidden)
        }

        is ForwardSource.Chat -> {
            when {
                messageId == null -> onEffect(ChatEffect.OpenChat(this.chatId))
                this.chatId == chatId -> showMessage(messageId)
                else -> onEffect(ChatEffect.OpenMessage(this.chatId, messageId))
            }
        }
    }
}
