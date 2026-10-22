package dev.kyralabs.pingogram.ui

import androidx.compose.runtime.Immutable

/**
 * What a pane of the Chats tab shows beside the list, as the pane navigator's content key ([key], a string, which the
 * navigator saves in a `Bundle`): the conversation of a chat ([Chat]), or beside it, in the extra pane, a user's
 * profile ([Profile]) or a group's or channel's info page ([ChatInfo]). Each names the chat whose conversation the
 * detail pane shows ([chatId]), so the detail pane knows it while the extra pane is the navigator's current one.
 */
@Immutable
internal sealed interface ChatsPaneContent {
    /** The chat whose conversation the detail pane shows. */
    val chatId: Long

    /** The navigator's content key. */
    val key: String

    /** The conversation of [chatId]. */
    data class Chat(
        override val chatId: Long,
    ) : ChatsPaneContent {
        override val key: String get() = "$CHAT:$chatId"
    }

    /** User [userId]'s profile beside the conversation of [chatId]. */
    data class Profile(
        override val chatId: Long,
        val userId: Long,
    ) : ChatsPaneContent {
        override val key: String get() = "$PROFILE:$chatId:$userId"
    }

    /** The info page of group or channel [infoChatId] beside the conversation of [chatId]. */
    data class ChatInfo(
        override val chatId: Long,
        val infoChatId: Long,
    ) : ChatsPaneContent {
        override val key: String get() = "$CHAT_INFO:$chatId:$infoChatId"
    }

    companion object {
        private const val CHAT = "chat"
        private const val PROFILE = "profile"
        private const val CHAT_INFO = "info"

        /** The content of navigator content key [key]; null for none, or a key this layout did not make. */
        fun of(key: String?): ChatsPaneContent? {
            val parts = key?.split(':').orEmpty()
            val ids = parts.drop(1).mapNotNull { it.toLongOrNull() }
            if (ids.size != parts.size - 1) return null
            return when {
                parts[0] == CHAT && ids.size == 1 -> Chat(ids[0])
                parts[0] == PROFILE && ids.size == 2 -> Profile(ids[0], ids[1])
                parts[0] == CHAT_INFO && ids.size == 2 -> ChatInfo(ids[0], ids[1])
                else -> null
            }
        }
    }
}
