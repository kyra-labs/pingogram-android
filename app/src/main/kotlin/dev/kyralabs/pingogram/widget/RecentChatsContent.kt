package dev.kyralabs.pingogram.widget

import android.content.res.Resources
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.shortName
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What the home screen widget of recent chats shows (`docs/adr/0017-home-screen-widget.md`): nothing yet, an invitation
 * to sign in, that the app is locked (a passcode keeps chats off the home screen too), or the most recent chats.
 */
@Immutable
sealed interface RecentChatsContent {
    /** TDLib has not said yet whether someone is signed in, or the chats are not loaded. */
    data object Loading : RecentChatsContent

    data object SignedOut : RecentChatsContent

    /** A passcode lock is set: the widget names no chat and shows no message. */
    data object Locked : RecentChatsContent

    data class Chats(
        val chats: List<WidgetChat>,
    ) : RecentChatsContent
}

/**
 * One row of the widget: the chat's avatar (null until it is drawn), its title, one line of its last message, and its
 * unread count, in the muted colors when the chat is muted.
 */
@Immutable
data class WidgetChat(
    val id: Long,
    val title: String,
    val preview: String,
    val unreadCount: Int,
    val hasUnreadMention: Boolean,
    val isMuted: Boolean,
    val avatar: Bitmap? = null,
    /** The account whose chat it is (`docs/adr/0031-multiple-accounts.md`), which the row opens it in. */
    val slot: Int = 0,
)

/**
 * The rows of the first [limit] [chats] of the main chat list, in its order (pinned first): "Saved Messages" for the
 * user's own chat, "Deleted Account" for one, and in groups "You:" or the sender's name before the message, as the
 * chat list does. A secret chat's messages stay off the home screen ("Secret chat"); a chat without messages shows an
 * empty line.
 */
internal fun widgetChats(
    chats: List<Chat>,
    users: Map<Long, User>,
    myUserId: Long?,
    resources: Resources,
    limit: Int,
    slot: Int = 0,
): List<WidgetChat> =
    chats.take(limit).map { chat ->
        val userId = (chat.type as? ChatType.Private)?.userId
        val isSavedMessages = userId != null && userId == myUserId
        val isDeletedAccount = userId != null && users[userId]?.type == UserType.Deleted
        WidgetChat(
            id = chat.id,
            title =
                when {
                    isSavedMessages -> resources.getString(UiR.string.chat_saved_messages)
                    isDeletedAccount -> resources.getString(UiR.string.user_deleted_account)
                    else -> chat.title
                },
            preview = chat.previewText(users, myUserId, resources),
            unreadCount = chat.unreadCount,
            hasUnreadMention = chat.unreadMentionCount > 0,
            isMuted = chat.isMuted,
            slot = slot,
        )
    }

private fun Chat.previewText(
    users: Map<Long, User>,
    myUserId: Long?,
    resources: Resources,
): String {
    val message = lastMessage
    return when {
        type is ChatType.Secret -> {
            resources.getString(R.string.widget_secret_chat)
        }

        message == null -> {
            ""
        }

        else -> {
            val name: (MessageSender) -> UiText? = { sender ->
                when (sender) {
                    is MessageSender.User -> users[sender.userId]?.shortName()
                    is MessageSender.Chat -> if (sender.chatId == id) UiText.Plain(title) else null
                }
            }
            val text = messagePreviewText(message, myUserId, name).resolve(resources)
            val sender = senderPrefix(message, users)?.resolve(resources)
            if (sender == null) text else resources.getString(R.string.widget_preview_sender, sender, text)
        }
    }
}

/** "You" or the sender's name before a message in a group; none elsewhere, for channel posts and service messages. */
private fun Chat.senderPrefix(
    message: Message,
    users: Map<Long, User>,
): UiText? {
    val type = type
    val isGroup = type is ChatType.BasicGroup || (type is ChatType.Supergroup && !type.isChannel)
    val sender = message.sender
    return when {
        !isGroup || message.isChannelPost || message.content is MessageContent.Service -> null
        message.isOutgoing -> UiText.resource(UiR.string.preview_sender_you)
        sender is MessageSender.User -> users[sender.userId]?.shortName()?.bidiIsolated()
        else -> null
    }
}
