package dev.kyralabs.pingogram.feature.calls.ui.comments

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A voice chat's or conference call's comments (`docs/DESIGN_SYSTEM.md` §6, "Voice chat comments"), kept out of
 * `GroupCallUiState` so a new comment recomposes the pane alone (`docs/PERFORMANCE.md` §4).
 */
@Immutable
sealed interface GroupCallCommentsUiState {
    /** No comments: no call, a live stream, or comments are off and the user may not turn them on. */
    data object Unavailable : GroupCallCommentsUiState

    data class Available(
        /** The pane shows: the header's "Comments" toggle, on this device only. */
        val isShown: Boolean,
        /** Comments are on in the call. */
        val areAllowed: Boolean,
        /** The user may write now (on, and under Telegram's limit of participants for comments). */
        val canComment: Boolean,
        /** A comment costs Telegram Stars, which Pingogram can't pay: the field says so instead. */
        val isPaid: Boolean = false,
        /** An administrator: comments on or off for everyone. */
        val canToggle: Boolean = false,
        /** An administrator: others' comments can be deleted. */
        val canModerate: Boolean = false,
        /** How many came since the user joined: "Comments · 214". */
        val received: Int = 0,
        /** The newest last. */
        val comments: ImmutableList<CommentUi> = persistentListOf(),
        /** The newest reactions (comments of one quick reaction), which float up from the pane's bottom end. */
        val reactions: ImmutableList<FloatingReaction> = persistentListOf(),
    ) : GroupCallCommentsUiState
}

/** One comment's row. */
@Immutable
data class CommentUi(
    val id: Int,
    /** The sender's key, "user:42" or "chat:-100": "Delete all from Ada" goes by it. */
    val senderKey: String,
    /** For the avatar's placeholder color. */
    val senderId: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val text: String,
    /** Its sender paid Telegram Stars to highlight it ("★ 50"); 0 for a free comment. */
    val paidStarCount: Long = 0,
    val isFromOwner: Boolean = false,
    val isOwn: Boolean = false,
    /** The user may delete it: their own, or as an administrator. */
    val canDelete: Boolean = false,
    /** Telegram refused it ("Not sent"). */
    val isFailed: Boolean = false,
)

/** A reaction floating up: its comment's id (a key) and its emoji. */
@Immutable
data class FloatingReaction(
    val id: Int,
    val emoji: String,
)

/** What screen readers hear of new comments, at most every few seconds: how many came and the newest. */
@Immutable
data class CommentAnnouncement(
    val count: Int,
    val name: String,
    val text: String,
)

/** What the user does in the comments pane. */
sealed interface CommentEvent {
    /** The header's toggle: shows or hides the pane on this device. */
    data object ToggleShown : CommentEvent

    data class Send(
        val text: String,
    ) : CommentEvent

    /** A quick reaction: sent as a comment of that emoji alone. */
    data class React(
        val emoji: String,
    ) : CommentEvent

    data class Delete(
        val id: Int,
        val reportSpam: Boolean,
    ) : CommentEvent

    /** Deletes every comment from the sender with this key, as an administrator. */
    data class DeleteAllFrom(
        val senderKey: String,
    ) : CommentEvent

    /** Turns comments on or off for everyone, as an administrator. */
    data class SetAllowed(
        val allowed: Boolean,
    ) : CommentEvent
}

/** One-off results: a refusal for a snackbar. */
sealed interface CommentEffect {
    data class Failed(
        val error: DomainError,
    ) : CommentEffect
}
