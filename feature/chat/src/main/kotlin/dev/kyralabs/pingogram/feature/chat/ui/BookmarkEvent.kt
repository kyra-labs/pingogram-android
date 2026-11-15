package dev.kyralabs.pingogram.feature.chat.ui

/**
 * What the bookmark sheet takes. [Open] comes from a message's actions, as a [ChatEvent] the route hands to
 * `bookmarks.BookmarkSheetViewModel` rather than the conversation's view model.
 */
sealed interface BookmarkEvent : ChatEvent {
    /** "Bookmark" on message [messageId]: the sheet opens. */
    data class Open(
        val messageId: Long,
    ) : BookmarkEvent

    /** A tag's chip: chosen or not. */
    data class ToggleTag(
        val id: Long,
    ) : BookmarkEvent

    /** "New tag": a tag named [name], chosen at once. */
    data class NewTag(
        val name: String,
    ) : BookmarkEvent

    /** "Save" with [note], and the message's [sender] and [text] as the sheet shows them, kept with the bookmark. */
    data class Save(
        val note: String,
        val sender: String,
        val text: String,
    ) : BookmarkEvent

    /** "Remove bookmark". */
    data object Remove : BookmarkEvent

    /** "Cancel", the scrim, dragging or back. */
    data object Dismiss : BookmarkEvent
}
