package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatNotification
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.NotificationContent
import dev.kyralabs.pingogram.core.domain.model.NotificationGroup
import dev.kyralabs.pingogram.core.domain.model.NotificationGroupType
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.model.WriteAccess
import dev.kyralabs.pingogram.core.domain.model.messageIds
import dev.kyralabs.pingogram.core.domain.model.writeAccess
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.shortName
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * What one notification of a chat shows (`REQUIREMENTS.md` FR-N2): a conversation in Android's terms, built from a
 * TDLib notification group ([ConversationNotificationFactory]) and drawn by [ConversationNotificationBuilder].
 *
 * - [title] is the chat's title; [isGroupConversation] for groups, whose messages name their senders.
 * - [messages] are the group's notifications, oldest first, each with its sender ([NotificationMessage.sender], null
 *   for the current user).
 * - [canReply] where the user may write; a reply quotes [replyToMessageId] (in groups, to make clear what it answers).
 *   Reply and mark as read act on [messageIds]; dismissing tells TDLib up to [maxNotificationId].
 * - [chatIcon] and [chatPerson] (private chats) describe the chat's conversation shortcut.
 *
 * `toString()` leaves names and texts out. `LongParameterList` is suppressed: it holds what one notification shows.
 */
@Suppress("LongParameterList")
internal class ConversationNotification(
    val groupId: Int,
    val chatId: Long,
    val channel: PingogramChannel,
    val title: String,
    val isGroupConversation: Boolean,
    val isCall: Boolean,
    val messages: List<NotificationMessage>,
    val totalCount: Int,
    val canReply: Boolean,
    val replyToMessageId: Long?,
    val messageIds: List<Long>,
    val maxNotificationId: Int,
    val chatIcon: IconCompat,
    val chatPerson: Person?,
) {
    override fun toString() = "ConversationNotification(groupId=$groupId, channel=$channel, ${messages.size} messages)"
}

/** One message of a [ConversationNotification], sent at [timeMillis] by [sender] (null: the current user). */
internal class NotificationMessage(
    val text: String,
    val timeMillis: Long,
    val sender: Person?,
)

/**
 * Builds the [ConversationNotification] of a TDLib notification group from what the repositories know: the chat, its
 * group or channel, the senders and their avatars ([AvatarIcons]). Texts are the chat list's previews
 * ([messagePreviewText]: "Photo", "Voice message", what a service message reports), shown only where TDLib allows a
 * preview (`showPreview`, the chat's notification setting); otherwise "New message".
 */
@Singleton
internal class ConversationNotificationFactory
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val chatRepository: ChatRepository,
        private val userRepository: UserRepository,
        private val groupRepository: GroupRepository,
        private val avatars: AvatarIcons,
    ) {
        private val resources get() = context.resources

        /** The notification of [group]. */
        suspend fun create(group: NotificationGroup): ConversationNotification {
            val chat = chatRepository.getChat(group.chatId).getOrNull()
            val users = userRepository.users.first()
            val senders = Senders(chat, users, userRepository.myUserId.value)
            val title = chat?.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.notification_unknown_chat)
            val chatIcon = avatars.icon(chat?.photo, group.chatId, title)
            val partner = chat?.partnerId?.let(users::get)
            val messages = group.notifications.map { notification -> message(notification, chat, senders) }
            val isGroupConversation = chat?.isGroup == true
            return ConversationNotification(
                groupId = group.id,
                chatId = group.chatId,
                channel = PingogramChannel.of(group.type, chat?.type),
                title = title,
                isGroupConversation = isGroupConversation,
                isCall = group.type == NotificationGroupType.Calls,
                messages = messages,
                totalCount = maxOf(group.totalCount, messages.size),
                canReply = group.type != NotificationGroupType.Calls && chat != null && canWrite(chat, partner),
                replyToMessageId = group.messageIds.lastOrNull()?.takeIf { isGroupConversation },
                messageIds = group.messageIds,
                maxNotificationId = group.notifications.maxOf { it.id },
                chatIcon = chatIcon,
                chatPerson = partner?.let { person(it.id, it.fullName().resolve(resources), chatIcon, it.isBot) },
            )
        }

        private suspend fun message(
            notification: ChatNotification,
            chat: Chat?,
            senders: Senders,
        ): NotificationMessage {
            val content = notification.content
            val (sender, text) =
                when (content) {
                    is NotificationContent.NewMessage -> {
                        val message = content.message
                        val sender = if (message.isOutgoing) null else person(message.sender, senders)
                        val text =
                            if (content.showPreview) {
                                messagePreviewText(message, senders.myUserId, senders::shortName).resolve(resources)
                            } else {
                                context.getString(R.string.notification_new_message)
                            }
                        sender to text
                    }

                    is NotificationContent.NewPushMessage -> {
                        val sender =
                            if (content.isOutgoing) {
                                null
                            } else {
                                content.sender?.let { person(it, senders) }
                                    ?: Person.Builder().setName(content.senderName).build()
                            }
                        sender to (content.text ?: context.getString(R.string.notification_new_message))
                    }

                    NotificationContent.NewSecretChat -> {
                        partnerPerson(chat, senders) to context.getString(R.string.notification_new_secret_chat)
                    }

                    is NotificationContent.NewCall -> {
                        partnerPerson(chat, senders) to context.getString(R.string.notification_incoming_call)
                    }
                }
            return NotificationMessage(text, notification.date.seconds.inWholeMilliseconds, sender)
        }

        /** The user of a private or secret chat; for a group or an unknown chat, nobody in particular. */
        private suspend fun partnerPerson(
            chat: Chat?,
            senders: Senders,
        ): Person? = chat?.partnerId?.let { person(MessageSender.User(it), senders) }

        /** A user, or a chat that sends as itself (a channel, a group's anonymous admins), with its avatar. */
        private suspend fun person(
            sender: MessageSender,
            senders: Senders,
        ): Person =
            when (sender) {
                is MessageSender.User -> {
                    val user = senders.users[sender.userId]
                    val name = user?.fullName()?.resolve(resources) ?: context.getString(R.string.notification_someone)
                    val icon = avatars.icon(user?.profilePhoto, sender.userId, name)
                    person(USER_KEY + sender.userId, name, icon, isBot = user?.isBot == true)
                }

                is MessageSender.Chat -> {
                    val chat =
                        senders.chat?.takeIf { it.id == sender.chatId }
                            ?: chatRepository.getChat(sender.chatId).getOrNull()
                    val name = chat?.title ?: context.getString(R.string.notification_someone)
                    val icon = avatars.icon(chat?.photo, sender.chatId, name)
                    person(CHAT_KEY + sender.chatId, name, icon, isBot = false)
                }
            }

        private fun person(
            key: String,
            name: String,
            icon: IconCompat,
            isBot: Boolean,
        ): Person =
            Person
                .Builder()
                .setKey(key)
                .setName(name)
                .setIcon(icon)
                .setBot(isBot)
                .build()

        private fun person(
            userId: Long,
            name: String,
            icon: IconCompat,
            isBot: Boolean,
        ): Person = person(USER_KEY + userId, name, icon, isBot)

        /** Whether the user may send a text to [chat], as its composer would allow ([writeAccess]). */
        private suspend fun canWrite(
            chat: Chat,
            partner: User?,
        ): Boolean {
            val type = chat.type
            val basicGroup = (type as? ChatType.BasicGroup)?.let { groupRepository.basicGroup(it.basicGroupId).first() }
            val supergroup = (type as? ChatType.Supergroup)?.let { groupRepository.supergroup(it.supergroupId).first() }
            return chat.writeAccess(partner, basicGroup, supergroup) == WriteAccess.Allowed
        }

        private companion object {
            // Keys of Person, so Android tells senders apart even with equal names.
            const val USER_KEY = "user:"
            const val CHAT_KEY = "chat:"
        }
    }

/** Who is who in the messages of one chat: the chat, the users TDLib knows, the current user. */
private class Senders(
    val chat: Chat?,
    val users: Map<Long, User>,
    val myUserId: Long?,
) {
    /** The short names of service messages ("Ada joined the group"), as in the chat list. */
    fun shortName(sender: MessageSender): UiText? =
        when (sender) {
            is MessageSender.User -> users[sender.userId]?.shortName()
            is MessageSender.Chat -> chat?.takeIf { it.id == sender.chatId }?.let { UiText.Plain(it.title) }
        }
}

/** The user of a private or secret chat; null for groups and channels. */
private val Chat.partnerId: Long?
    get() =
        when (val type = type) {
            is ChatType.Private -> type.userId
            is ChatType.Secret -> type.userId
            else -> null
        }

/** A basic group or a supergroup that is not a channel: a conversation of several people. */
private val Chat.isGroup: Boolean
    get() = type is ChatType.BasicGroup || (type as? ChatType.Supergroup)?.isChannel == false

private val User.isBot: Boolean
    get() = type == UserType.Bot
