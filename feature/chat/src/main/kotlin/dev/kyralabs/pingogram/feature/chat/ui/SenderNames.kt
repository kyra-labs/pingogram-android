package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageOrigin
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.shortName
import dev.kyralabs.pingogram.core.ui.theme.SenderHues

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

/**
 * The experimental look's hue of a sender (`SenderHues.indexOf`, `docs/DESIGN_SYSTEM.md` §13.6): from a looked-up
 * user's Telegram accent color ([ref]), else from the user or chat [id].
 */
internal fun senderHueOf(
    ref: Any?,
    id: Long,
): Int = SenderHues.indexOf((ref as? User)?.accentColorId ?: -1, id)

/** Whether this text mentions user [userId] by name (td_api.tl `textEntityTypeMentionName`). */
internal fun FormattedText.mentions(userId: Long?): Boolean =
    userId != null && entities.any { (it.type as? TextEntityType.MentionName)?.userId == userId }

/** The sender's hue where the bubble [showsSender] (incoming in a group), else -1. */
internal fun ItemInputs.senderHue(showsSender: Boolean): Int =
    if (showsSender) senderHueOf(sender, message.sender.id) else -1

/** Whether the message mentions the user: TDLib's unread mention, or a mention by name in its [text]. */
internal fun ItemInputs.mentionsMe(text: FormattedText): Boolean =
    message.containsUnreadMention || text.mentions(myUserId)

/**
 * Where [text] mentions the user: by name (td_api.tl `textEntityTypeMentionName`) or by one of their usernames
 * (`textEntityTypeMention`), as ranges of the text; empty in a message that does not [mentionsMe].
 */
internal fun ItemInputs.myMentions(
    text: FormattedText,
    mentionsMe: Boolean,
): List<IntRange> {
    if (!mentionsMe) return emptyList()
    return text.entities.mapNotNull { entity ->
        val start = entity.offset.coerceIn(0, text.text.length)
        val end = (entity.offset.toLong() + entity.length).coerceIn(start.toLong(), text.text.length.toLong()).toInt()
        val mine =
            when (val type = entity.type) {
                is TextEntityType.MentionName -> {
                    type.userId == myUserId
                }

                TextEntityType.Mention -> {
                    text.text
                        .substring(start, end)
                        .removePrefix("@")
                        .lowercase() in myUsernames
                }

                else -> {
                    false
                }
            }
        (start until end).takeIf { mine && end > start }
    }
}

/** How many saw [newest], a channel post (its views); 0 elsewhere. */
internal fun ItemInputs.views(newest: Message): Int =
    if (kind == ChatKind.Channel) newest.interactionInfo?.viewCount ?: 0 else 0
