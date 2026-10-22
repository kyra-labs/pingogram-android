package dev.kyralabs.pingogram.feature.chat.ui.pinned

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/**
 * The pinned messages bar over the conversation (`REQUIREMENTS.md` FR-C9): pinned message [messageId], the [index]th
 * of [count] counted from the newest (0), with its [sender] (null where the chat itself posts, as a channel does) and
 * one line of it ([preview]). [canUnpin] where the user may unpin it, which the close button then does; otherwise it
 * only hides the bar.
 */
@Immutable
data class PinnedBar(
    val messageId: Long,
    val index: Int,
    val count: Int,
    val sender: UiText?,
    val preview: UiText,
    val canUnpin: Boolean,
) {
    override fun toString() = "PinnedBar(messageId=$messageId, index=$index, count=$count, canUnpin=$canUnpin, …)"
}

/** The sheet of every pinned message of the chat, newest first ([rows]); [shownId] is the one the bar shows. */
@Immutable
data class PinnedSheet(
    val rows: ImmutableList<PinnedRow>,
    val shownId: Long?,
)

/** One pinned message in the [PinnedSheet]: its [sender] (null where the chat itself posts) and [preview]. */
@Immutable
data class PinnedRow(
    val messageId: Long,
    val sender: UiText?,
    val preview: UiText,
) {
    override fun toString() = "PinnedRow(messageId=$messageId, …)"
}
