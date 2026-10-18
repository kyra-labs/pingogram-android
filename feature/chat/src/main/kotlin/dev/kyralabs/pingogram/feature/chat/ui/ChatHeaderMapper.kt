package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.chatActionText
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.core.ui.text.shortName
import java.time.Instant
import dev.kyralabs.pingogram.feature.chat.R as ChatR

/**
 * The top bar of the conversation (`REQUIREMENTS.md` FR-C8): the chat's name and avatar, and as subtitle, by
 * precedence, what people are doing in it, then its status ([status]). Saved Messages has neither.
 */
internal fun HeaderSources.toHeader(): ChatHeader {
    val isSavedMessages = chat.type is ChatType.Private && chat.userId == myUserId
    val isDeletedAccount = user?.isDeleted == true
    val action =
        actions.takeIf { !isSavedMessages }?.let { actions ->
            chatActionText(actions, chat.isGroup) { sender -> senderShortName(sender) }
        }
    return ChatHeader(
        chatId = chat.id,
        title =
            when {
                isSavedMessages -> UiText.resource(R.string.chat_saved_messages)
                isDeletedAccount -> UiText.resource(R.string.user_deleted_account)
                else -> UiText.Plain(chat.title)
            },
        photo = if (isSavedMessages) null else chat.photo,
        placeholderIcon =
            when {
                isSavedMessages -> R.drawable.ic_bookmark_filled
                isDeletedAccount -> R.drawable.ic_person_filled
                else -> null
            },
        subtitle =
            when {
                isSavedMessages -> null
                action != null -> ChatSubtitle.Action(action)
                else -> status()?.let(ChatSubtitle::Status)
            },
    )
}

/**
 * What the header is made from, as of one emission: the chat, the member count of a group or channel (null while
 * unknown), what people are doing in the chat, the users (the chat's user, the names of people typing) and the
 * current user, and the minute.
 */
internal class HeaderSources(
    val chat: Chat,
    val memberCount: Int?,
    val actions: List<SenderAction>,
    val people: ChatPeople,
    val now: Instant,
    val timeFormatter: ChatTimeFormatter,
) {
    /** The user of a private or secret chat; null for groups and channels, and while TDLib has not announced it. */
    val user: User? get() = chat.userId?.let(people.users::get)

    val myUserId: Long? get() = people.myUserId
}

/**
 * What the chat is: "online" or "last seen …" for a user, "bot", "N members" for a group and "N subscribers" for a
 * channel ("group" and "channel" while the count is unknown); null for a deleted account, or a user not known yet.
 */
private fun HeaderSources.status(): UiText? =
    when (val type = chat.type) {
        is ChatType.Private, is ChatType.Secret -> user?.let { userStatus(it) }
        is ChatType.BasicGroup -> members(isChannel = false)
        is ChatType.Supergroup -> members(type.isChannel)
    }

private fun HeaderSources.userStatus(user: User): UiText? =
    when {
        user.isDeleted -> null
        user.type == UserType.Bot -> UiText.resource(ChatR.string.chat_subtitle_bot)
        else -> lastSeen(user.status)
    }

/**
 * "online" until the status expires; then, and for offline users, when they were last online; the bucket for a time
 * the user's privacy settings hide.
 */
private fun HeaderSources.lastSeen(status: UserStatus): UiText =
    when (status) {
        is UserStatus.Online -> {
            if (status.expiresAtEpochSeconds > now.epochSecond) {
                UiText.resource(ChatR.string.chat_subtitle_online)
            } else {
                timeFormatter.lastSeen(status.expiresAtEpochSeconds, now)
            }
        }

        is UserStatus.Offline -> {
            timeFormatter.lastSeen(status.wasOnlineAtEpochSeconds, now)
        }

        UserStatus.Recently -> {
            UiText.resource(ChatR.string.chat_subtitle_last_seen_recently)
        }

        UserStatus.LastWeek -> {
            UiText.resource(ChatR.string.chat_subtitle_last_seen_week)
        }

        UserStatus.LastMonth -> {
            UiText.resource(ChatR.string.chat_subtitle_last_seen_month)
        }

        UserStatus.Empty -> {
            UiText.resource(ChatR.string.chat_subtitle_last_seen_long_ago)
        }
    }

private fun HeaderSources.members(isChannel: Boolean): UiText {
    val count = memberCount?.takeIf { it > 0 }
    return when {
        count == null && isChannel -> UiText.resource(ChatR.string.chat_subtitle_channel)
        count == null -> UiText.resource(ChatR.string.chat_subtitle_group)
        isChannel -> UiText.plural(ChatR.plurals.chat_subtitle_subscribers, count, count)
        else -> UiText.plural(ChatR.plurals.chat_subtitle_members, count, count)
    }
}

/** The short name of someone doing something: a user's first name, or the title of the chat they act as. */
private fun HeaderSources.senderShortName(sender: MessageSender): UiText? =
    when (sender) {
        is MessageSender.User -> people.users[sender.userId]?.shortName()
        is MessageSender.Chat -> if (sender.chatId == chat.id) UiText.Plain(chat.title) else null
    }

/** The user of a private or secret chat; null for groups and channels. */
internal val Chat.userId: Long?
    get() =
        when (val type = type) {
            is ChatType.Private -> type.userId
            is ChatType.Secret -> type.userId
            else -> null
        }

/** A basic group or a supergroup that is not a channel: messages there show who sent them. */
internal val Chat.isGroup: Boolean
    get() = type is ChatType.BasicGroup || (type as? ChatType.Supergroup)?.isChannel == false

internal val Chat.isChannel: Boolean
    get() = (type as? ChatType.Supergroup)?.isChannel == true
