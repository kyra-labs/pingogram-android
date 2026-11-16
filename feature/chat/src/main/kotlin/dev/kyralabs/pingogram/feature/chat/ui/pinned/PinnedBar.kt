package dev.kyralabs.pingogram.feature.chat.ui.pinned

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.ui.text.UiText

/**
 * The pinned messages bar over the conversation (`REQUIREMENTS.md` FR-C9): pinned message [messageId] at [position] of
 * [count], counted from the oldest (0) as the list shows them, so the newest is the last; with its [sender] (null where
 * the chat itself posts, as a channel does) and one line of it ([preview]). [canUnpin] where the user may unpin it,
 * which the close button then does; otherwise it only hides the bar. [thumbnail]: a small picture of its photo, video,
 * GIF or round video, or a file's tile, before the text.
 */
@Immutable
data class PinnedBar(
    val messageId: Long,
    val position: Int,
    val count: Int,
    val sender: UiText?,
    val preview: UiText,
    val canUnpin: Boolean,
    val thumbnail: PinnedThumbnail? = null,
) {
    override fun toString() = "PinnedBar(messageId=$messageId, position=$position, count=$count, canUnpin=$canUnpin, …)"
}
