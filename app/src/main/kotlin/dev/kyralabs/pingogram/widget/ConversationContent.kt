package dev.kyralabs.pingogram.widget

import android.content.res.Resources
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.keyword.MutedWords
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.repository.shownName
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.shortName
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What a Conversation widget (board 33) shows: one chat's newest messages and a way to reply, or why it shows none.
 * Each widget keeps its chat and the account it is in.
 */
@Immutable
sealed interface ConversationContent {
    /** TDLib has not said yet whether someone is signed in, or the chat's messages are on their way. */
    data object Loading : ConversationContent

    /** Nobody is signed in to the widget's account: it asks to sign in first. */
    data object SignedOut : ConversationContent

    /** A passcode lock is set, or the chat is a locked chat: the widget names no chat and shows no message. */
    data object Locked : ConversationContent

    /** The widget keeps no chat yet (a widget just placed): it asks to choose one. */
    data object NoChat : ConversationContent

    /** The messages could not be loaded (no network, a refusal): a tap tries again. */
    data object Failed : ConversationContent

    /**
     * Chat [chatId] of the account in [slot]: its [title], [avatar] (null until it is drawn), unread count, whether it
     * is muted, and its newest [messages], oldest first; [canReply] where the user may write in it. A secret chat's
     * messages stay off the home screen ([isSecret]).
     */
    data class Chat(
        val chatId: Long,
        val slot: Int,
        val title: String,
        val unreadCount: Int,
        val isMuted: Boolean,
        val messages: List<WidgetMessage>,
        val canReply: Boolean,
        val isSecret: Boolean = false,
        val avatar: Bitmap? = null,
    ) : ConversationContent
}

/** One message of a Conversation widget: who sent it ("You" for the user's own; none in channels), and its text. */
@Immutable
data class WidgetMessage(
    val id: Long,
    val sender: String?,
    val text: String,
)

/**
 * [chat]'s newest messages for its Conversation widget, newest first: [history]'s while it reaches the chat's newest
 * message. While it does not, as the user reads further back in the app (the history keeps a bounded window) or opened
 * the chat at an older message, the ones [kept] from when it did, with the chat's last message joining them as new ones
 * come.
 */
internal fun newestMessages(
    chat: Chat,
    history: ChatHistory,
    kept: List<Message>,
    limit: Int = MAX_MESSAGES,
): List<Message> =
    if (history.reachedEnd) {
        history.messages.take(limit)
    } else {
        (listOfNotNull(chat.lastMessage) + kept).distinctBy { it.id }.sortedByDescending { it.id }.take(limit)
    }

/**
 * [chat]'s newest [limit] [messages] (newest first, as the history keeps them) for its Conversation widget, oldest
 * first, as a conversation reads: each with "You" or its sender's short name, none for a channel's posts and service
 * messages, and its text as the chat list words it, or that it has a muted word ([mutedWords], FR-X21). A secret
 * chat's are left out.
 */
internal fun widgetMessages(
    chat: Chat,
    messages: List<Message>,
    users: Map<Long, User>,
    myUserId: Long?,
    resources: Resources,
    mutedWords: MutedWords? = null,
    limit: Int = MAX_MESSAGES,
): List<WidgetMessage> {
    if (chat.type is ChatType.Secret) return emptyList()
    val name: (MessageSender) -> UiText? = { sender ->
        when (sender) {
            is MessageSender.User -> users[sender.userId]?.shortName()
            is MessageSender.Chat -> if (sender.chatId == chat.id) UiText.Plain(chat.title) else null
        }
    }
    return messages.take(limit).reversed().map { message ->
        WidgetMessage(
            id = message.id,
            sender = senderOf(chat, message, users)?.resolve(resources),
            text =
                if (mutedWords?.mutes(chat, message, myUserId?.let(users::get)) == true) {
                    resources.getString(UiR.string.preview_muted_word)
                } else {
                    messagePreviewText(message, myUserId, name).resolve(resources)
                },
        )
    }
}

/**
 * The widget's title for [chat]: "Saved Messages" for the user's own chat, "Deleted Account" for one, and the name
 * given on this phone ([localNames], `REQUIREMENTS.md` FR-X20) unless the chat is a contact's.
 */
internal fun widgetTitle(
    chat: Chat,
    users: Map<Long, User>,
    myUserId: Long?,
    resources: Resources,
    localNames: Map<Long, String> = emptyMap(),
): String {
    val userId = (chat.type as? ChatType.Private)?.userId
    return when {
        userId != null && userId == myUserId -> {
            resources.getString(UiR.string.chat_saved_messages)
        }

        userId != null && users[userId]?.type == UserType.Deleted -> {
            resources.getString(UiR.string.user_deleted_account)
        }

        else -> {
            localNames.shownName(chat.id, isContact = userId != null && users[userId]?.isContact == true) ?: chat.title
        }
    }
}

/** "You", or the sender's short name; none for a channel's posts and service messages, which name no sender. */
private fun senderOf(
    chat: Chat,
    message: Message,
    users: Map<Long, User>,
): UiText? {
    val type = chat.type
    val isChannel = type is ChatType.Supergroup && type.isChannel
    val sender = message.sender
    return when {
        isChannel || message.isChannelPost || message.content is MessageContent.Service -> null
        message.isOutgoing -> UiText.resource(UiR.string.preview_sender_you)
        sender is MessageSender.User -> users[sender.userId]?.shortName()
        sender is MessageSender.Chat && sender.chatId == chat.id -> UiText.Plain(chat.title)
        else -> null
    }
}

/** The most messages a Conversation widget shows: three at 4 × 2 (board 33). */
internal const val MAX_MESSAGES = 3
