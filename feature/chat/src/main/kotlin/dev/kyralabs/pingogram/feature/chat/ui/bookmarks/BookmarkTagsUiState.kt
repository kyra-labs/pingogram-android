package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.ImmutableList

/** Editing the bookmarks' tags (the owner's board 34c). */
@Immutable
sealed interface BookmarkTagsUiState {
    /** The tags are being read. */
    data object Loading : BookmarkTagsUiState

    /**
     * The tags in the user's order ([tags]), the one being written ([draft]: a new one, or a tag renamed) and the one
     * whose deletion is asked about ([deleting]).
     */
    data class Content(
        val tags: ImmutableList<TagRow>,
        val draft: TagDraft? = null,
        val deleting: TagRow? = null,
    ) : BookmarkTagsUiState
}

/**
 * A tag's row: its [id], [name] and sender hue [color], how many bookmarks have it ([count]) and how many have it alone
 * ([alone]), which lose their only tag when it is deleted.
 */
@Immutable
data class TagRow(
    val id: Long,
    val name: String,
    val color: Int,
    val count: Int,
    val alone: Int,
)

/** A tag being written: a new one ([id] null) or tag [id] renamed, with its [name] so far and its [color]. */
@Immutable
data class TagDraft(
    val id: Long?,
    val name: String,
    val color: Int,
) {
    /** A name to save: not blank, and at most [BookmarkTagsLimits.NAME_LENGTH_MAX] characters. */
    val canSave: Boolean get() = name.isNotBlank() && name.length <= BookmarkTagsLimits.NAME_LENGTH_MAX
}

/** How long a tag's name may be. */
object BookmarkTagsLimits {
    const val NAME_LENGTH_MAX = 32
}

/** What the tag editor takes. */
sealed interface BookmarkTagsEvent {
    /** "New tag" in the top bar. */
    data object StartNew : BookmarkTagsEvent

    /** A tag's edit button. */
    data class StartEdit(
        val id: Long,
    ) : BookmarkTagsEvent

    /** The draft's name field. */
    data class EditName(
        val name: String,
    ) : BookmarkTagsEvent

    /** A color swatch. */
    data class EditColor(
        val color: Int,
    ) : BookmarkTagsEvent

    /** The draft's check, or the keyboard's Done. */
    data object SaveDraft : BookmarkTagsEvent

    /** The draft's close, or back while it shows. */
    data object CancelDraft : BookmarkTagsEvent

    /** The tags in the order a drag left them. */
    data class Reorder(
        val ids: List<Long>,
    ) : BookmarkTagsEvent

    /** "Move up" or "Move down" for screen readers. */
    data class Move(
        val id: Long,
        val up: Boolean,
    ) : BookmarkTagsEvent

    /** A tag's delete button: asks first. */
    data class AskDelete(
        val id: Long,
    ) : BookmarkTagsEvent

    /** "Delete tag" in the question. */
    data object ConfirmDelete : BookmarkTagsEvent

    /** "Cancel" in the question, or dismissing it. */
    data object CancelDelete : BookmarkTagsEvent
}

/** What the tag editor says once. */
sealed interface BookmarkTagsEffect {
    /** "Tag deleted". */
    data class Deleted(
        val name: String,
    ) : BookmarkTagsEffect

    data class ShowError(
        val error: DomainError,
    ) : BookmarkTagsEffect
}

/** Whether a tag can move up or down, and what moving it does ([onMove] with `true` for up), for screen readers. */
internal class TagMoves(
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val onMove: (up: Boolean) -> Unit,
)
