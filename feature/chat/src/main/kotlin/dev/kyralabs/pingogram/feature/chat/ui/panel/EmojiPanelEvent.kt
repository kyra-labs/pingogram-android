package dev.kyralabs.pingogram.feature.chat.ui.panel

import dev.kyralabs.pingogram.core.domain.model.Animation

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

    /** The GIF tab's search field now holds [query]; blank shows the saved GIFs again. */
    data class GifQueryChanged(
        val query: String,
    ) : EmojiPanelEvent

    /** The GIF search's results near their end: the next page loads. */
    data object MoreGifs : EmojiPanelEvent

    /** "Try again" after the saved GIFs or a search's page could not be loaded. */
    data object RetryGifs : EmojiPanelEvent

    /** "Remove" in a saved GIF's menu: [gif] leaves the saved GIFs. */
    data class RemoveSavedGif(
        val gif: Animation,
    ) : EmojiPanelEvent
}
