package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageOrigin
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R as ChatR

// A channel's posts in its discussion group, which Telegram forwards there as the channel itself: the bubble names
// the channel with a "Channel" label (`docs/DESIGN_SYSTEM.md` "Role labels"), not where it was forwarded from.

/**
 * Whether the channel that sent this message is the one it was forwarded from: a post of a channel in its discussion
 * group.
 */
internal val Message.isFromItsChannel: Boolean
    get() {
        val channel = (forwardInfo?.origin as? MessageOrigin.Channel)?.chatId ?: return false
        return channel == (sender as? MessageSender.Chat)?.chatId
    }

/** The label after the sender's name where it [shows]: "Channel" on a channel's post in its discussion group. */
internal fun ItemInputs.shownRole(shows: Boolean): UiText? =
    when {
        !shows -> null
        message.isFromItsChannel -> UiText.resource(ChatR.string.chat_sender_channel)
        else -> senderRole
    }
