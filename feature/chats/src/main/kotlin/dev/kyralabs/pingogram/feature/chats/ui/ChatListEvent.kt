package dev.kyralabs.pingogram.feature.chats.ui

/** What the user does on the chat list, sent to [ChatListViewModel.onEvent]. */
sealed interface ChatListEvent {
    /** A folder tab was selected; null selects "All chats". */
    data class SelectFolder(
        val folderId: Int?,
    ) : ChatListEvent

    /** The list scrolled to within a page of its end: load the next chats, unless they are loading or all loaded. */
    data object LoadMore : ChatListEvent

    /** "Try again" after loading the chats failed. */
    data object Retry : ChatListEvent
}
