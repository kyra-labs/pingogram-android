package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.ChatListType
import kotlinx.coroutines.flow.StateFlow

/**
 * The chat list the Chats tab shows, the main list or a folder, so a conversation opened from it goes on in the same
 * list: a read channel offers the next unread channel of that folder (`docs/DESIGN_SYSTEM.md` §6 "Next unread
 * channel"). Kept in memory for the process, the main list until the Chats tab says otherwise.
 */
interface ShownChatListRepository {
    /** The list shown, then every change. */
    val shown: StateFlow<ChatListType>

    /** Says the Chats tab shows [list]. */
    fun show(list: ChatListType)
}
