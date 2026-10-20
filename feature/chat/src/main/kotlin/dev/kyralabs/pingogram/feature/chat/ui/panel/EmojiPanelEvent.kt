package dev.kyralabs.pingogram.feature.chat.ui.panel

/** What the user does in the emoji and sticker panel, for [EmojiPanelViewModel]. */
sealed interface EmojiPanelEvent {
    /** The panel opened: its emoji and stickers load, the first time. */
    data object Opened : EmojiPanelEvent

    /** The user put [emoji] into the text: it becomes the first recent one. */
    data class EmojiPicked(
        val emoji: String,
    ) : EmojiPanelEvent

    /** The sections of sticker sets [setIds] are on screen, or about to be: their stickers load. */
    data class SetsVisible(
        val setIds: List<Long>,
    ) : EmojiPanelEvent

    /** "Try again" after the stickers could not be loaded. */
    data object Retry : EmojiPanelEvent
}
