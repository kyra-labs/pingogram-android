package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SecretChat
import dev.kyralabs.pingogram.core.domain.model.SecretChatState
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.chatActionText
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.core.ui.text.shortName
import dev.kyralabs.pingogram.core.ui.text.stateText
import dev.kyralabs.pingogram.feature.chat.ui.secret.SecretChatHeader
import java.time.Instant
import dev.kyralabs.pingogram.feature.chat.R as ChatR

/**
 * The top bar of the conversation (`REQUIREMENTS.md` FR-C8): the chat's name and avatar, and as subtitle, by
 * precedence, what people are doing in it, then its status ([status]). Saved Messages has neither. A secret chat
 * (FR-E1) has its lock and self-destruct timer, and while it waits for the other user or after it ended, its state
 * ("Waiting for Ada to come online", "Secret chat ended") in place of the status. The title of a private or secret
 * chat opens the other user's profile, that of Saved Messages the user's own (its shared media, "Export chat"), that
 * of a group or channel its info page.
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
                else -> (secretState() ?: status())?.let(ChatSubtitle::Status)
            },
        profileUserId = chat.userId,
        secret = secretHeader(),
        opensChatInfo = chat.hasInfoPage,
        callUserId = callUserId().takeUnless { isSavedMessages },
    )
}

/**
 * The other user of a private chat with a person, not a bot, a deleted account or Telegram's service notifications;
 * their privacy settings decide once called.
 */
private fun HeaderSources.callUserId(): Long? =
    chat.userId.takeIf {
        val user = user
        chat.type is ChatType.Private &&
            user != null &&
            user.type == UserType.Regular &&
            !user.isDeleted &&
            !user.isSupport
    }

/**
 * The lock, state and timer of a secret chat; null for other chats. One TDLib has not announced counts as pending:
 * its lock and secure window show from the start.
 */
private fun HeaderSources.secretHeader(): SecretChatHeader? =
    if (chat.type is ChatType.Secret) {
        SecretChatHeader(secretChat?.state ?: SecretChatState.Pending, chat.messageAutoDeleteTime)
    } else {
        null
    }

/** What a secret chat that cannot be written to says; null for other chats and once it is ready. */
private fun HeaderSources.secretState(): UiText? = secretChat?.stateText(user?.shortName() ?: UiText.Plain(chat.title))

/**
 * What the header is made from, as of one emission: the chat, the member count of a group or channel (null while
 * unknown), the secret chat of a secret chat (null for other chats, and while unknown), what people are doing in the
 * chat, the users (the chat's user, the names of people typing) and the current user, and the minute.
 */
internal class HeaderSources(
    val chat: Chat,
    val memberCount: Int?,
    val secretChat: SecretChat? = null,
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
        else -> timeFormatter.userStatus(user.status, now)
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

/** Whether the chat is a group or channel, whose info page its title opens. */
private val Chat.hasInfoPage: Boolean
    get() = type is ChatType.BasicGroup || type is ChatType.Supergroup
