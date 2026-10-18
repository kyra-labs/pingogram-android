package dev.kyralabs.pingogram.feature.chats.ui

/** What the user does on the chat list, sent to [ChatListViewModel.onEvent]. */
sealed interface ChatListEvent {
    /** A folder tab was selected; null selects "All chats". */
    data class SelectFolder(
        val folderId: Int?,
    ) : ChatListEvent

    /**
     * Whether the list shows its end or is within about a page of it; reported on every change, and false when the
     * list leaves the screen. While true, the next chats load, page after page, until all are loaded.
     */
    data class NearEndChanged(
        val isNearEnd: Boolean,
    ) : ChatListEvent

    /** "Try again" after loading the chats failed. */
    data object Retry : ChatListEvent
}
