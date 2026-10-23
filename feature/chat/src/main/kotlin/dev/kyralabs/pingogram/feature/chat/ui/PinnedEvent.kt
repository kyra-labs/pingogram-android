package dev.kyralabs.pingogram.feature.chat.ui

/** What the user does with the pinned messages bar and its list (`REQUIREMENTS.md` FR-C9), sent to [ChatViewModel]. */
sealed interface PinnedEvent : ChatEvent {
    /** The bar was tapped: the list shows the pinned message it shows, and the bar moves on to the next older one. */
    data object ShowCurrent : PinnedEvent

    /**
     * The bar's close button: the pinned message it shows is unpinned where the user may unpin it; otherwise the bar
     * hides on this device until a newer message is pinned.
     */
    data object Close : PinnedEvent

    /** The bar's list button: the sheet of every pinned message opens. */
    data object OpenList : PinnedEvent

    /** A pinned message tapped in the sheet: the sheet closes, and the list and the bar show [messageId]. */
    data class ShowFromList(
        val messageId: Long,
    ) : PinnedEvent

    /** The sheet was dismissed. */
    data object DismissList : PinnedEvent

    /**
     * The user started dragging the list: the bar follows the messages on screen again, after a tap or the sheet chose
     * its message.
     */
    data object ListDragged : PinnedEvent
}
