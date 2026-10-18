package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.MessageOrigin
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.shortName

// How the items name the users and chats they refer to, from what ItemInputs looked up.

/** The full name of a looked-up user, the title of a chat, or "Someone". */
internal fun fullNameOf(ref: Any?): UiText =
    when (ref) {
        is User -> ref.fullName()
        is ChatRef -> UiText.Plain(ref.title)
        else -> someone()
    }

/** Who [origin] is: a hidden user by the name it left, else the looked-up user or chat ([ref]). */
internal fun originName(
    origin: MessageOrigin,
    ref: Any?,
): UiText =
    when (origin) {
        is MessageOrigin.HiddenUser -> origin.name.takeIf(String::isNotBlank)?.let(UiText::Plain) ?: someone()
        else -> fullNameOf(ref)
    }

/**
 * What service messages and previews call a sender: a user's short name, or the title of the chat; null ("Someone")
 * for anyone this item did not look up (its sender, the members it names, the replied message's sender).
 */
internal fun ItemInputs.shortNameOf(sender: MessageSender): UiText? {
    val known = listOf(this.sender, reply?.repliedSender) + members
    return when (sender) {
        is MessageSender.User -> {
            known.filterIsInstance<User>().firstOrNull { it.id == sender.userId }?.shortName()
        }

        is MessageSender.Chat -> {
            val chat = known.filterIsInstance<ChatRef>().firstOrNull { it.id == sender.chatId }
            chat?.let { UiText.Plain(it.title) }
        }
    }
}

private fun someone() = UiText.resource(R.string.preview_someone)

/** The id of a user or chat, which picks colors and placeholders. */
internal val MessageSender.id: Long
    get() =
        when (this) {
            is MessageSender.User -> userId
            is MessageSender.Chat -> chatId
        }

/** The id of the user or chat a message comes from; null for a hidden user. */
internal val MessageOrigin.id: Long?
    get() =
        when (this) {
            is MessageOrigin.User -> userId
            is MessageOrigin.HiddenUser -> null
            is MessageOrigin.Chat -> chatId
            is MessageOrigin.Channel -> chatId
        }
