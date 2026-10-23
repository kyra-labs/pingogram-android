package dev.kyralabs.pingogram.feature.calls.ui

import kotlinx.coroutines.flow.Flow

/** Chats as the voice chat's screen shows them, its own and those participants joined as; a seam for its tests. */
fun interface GroupCallChats {
    /** Chat [chatId]'s title and photo, then every change; null while TDLib has not announced the chat. */
    fun chat(chatId: Long): Flow<GroupCallChat?>
}
