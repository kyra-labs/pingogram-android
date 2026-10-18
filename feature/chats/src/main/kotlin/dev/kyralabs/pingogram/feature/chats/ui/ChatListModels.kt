package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.DeleteChatAction
import dev.kyralabs.pingogram.core.domain.model.DeleteChatOptions
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.toImmutableList

// What ChatListViewModel builds the archive row and the delete dialog from.

/**
 * The archive row for the archive's [count] and the archived chats loaded so far ([chats], in TDLib's order); null
 * while the archive has no chats. TDLib's count is an estimate, and may come after the first chats.
 */
internal fun archiveEntry(
    count: UnreadChatCount,
    chats: List<Chat>,
): ArchiveEntry? {
    val chatCount = maxOf(count.totalCount, chats.size)
    if (chatCount == 0) return null
    val titles =
        chats
            .asSequence()
            .map { it.title }
            .filter { it.isNotBlank() }
            .take(ARCHIVE_ROW_TITLES)
            .toImmutableList()
    return ArchiveEntry(chatCount, titles, count.unreadCount)
}

/**
 * The confirmation for deleting [chat], whose row shows [title], with what TDLib allows ([options]): leaving a group or
 * channel, or deleting the conversation; deleting it for everyone where allowed, for the other user of a private or
 * secret chat, or for all members or subscribers of a group or channel the user owns.
 */
internal fun deleteDialog(
    chat: Chat,
    title: UiText,
    options: DeleteChatOptions,
): DeleteDialog.Single {
    val type = chat.type
    val isChannel = type is ChatType.Supergroup && type.isChannel
    val isGroup = type is ChatType.BasicGroup || (type is ChatType.Supergroup && !type.isChannel)
    val kind =
        when {
            options.action == DeleteChatAction.Delete -> DeleteKind.DeleteChat
            isChannel -> DeleteKind.LeaveChannel
            else -> DeleteKind.LeaveGroup
        }
    val forEveryone =
        when {
            !options.canDeleteForEveryone -> null
            isChannel -> DeleteForEveryone.AllSubscribers
            isGroup -> DeleteForEveryone.AllMembers
            else -> DeleteForEveryone.OtherUser
        }
    return DeleteDialog.Single(chat.id, title, kind, forEveryone)
}

/** How many archived chats the archive row names: as many as fit on its line. */
private const val ARCHIVE_ROW_TITLES = 3
