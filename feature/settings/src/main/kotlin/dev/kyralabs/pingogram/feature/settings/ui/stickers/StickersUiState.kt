package dev.kyralabs.pingogram.feature.settings.ui.stickers

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerSetSummary
import kotlinx.collections.immutable.ImmutableList

/** What Settings → Stickers shows (`REQUIREMENTS.md` FR-C5). */
@Immutable
sealed interface StickersUiState {
    data object Loading : StickersUiState

    /** The installed sets could not be loaded, because of [error]. */
    data class Error(
        val error: DomainError,
    ) : StickersUiState

    /**
     * The user's installed sets ([installed]) in their order, how many sets they archived ([archivedCount]) and how
     * many trending sets they have not seen yet ([newTrendingCount]); a count is null while it loads or when it
     * failed, as both rows open their pages anyway.
     */
    data class Content(
        val installed: ImmutableList<StickerSetItem>,
        val archivedCount: Int? = null,
        val newTrendingCount: Int? = null,
    ) : StickersUiState
}

/**
 * A sticker set of a list: its [title] (user content), its short [name] for its link, how many stickers it has
 * ([count]) and its [cover]; [isInstalled] once the user has it, [isBusy] while Telegram is asked to change it.
 */
@Immutable
data class StickerSetItem(
    val id: Long,
    val title: String,
    val name: String,
    val count: Int,
    val cover: Sticker?,
    val isInstalled: Boolean = true,
    val isBusy: Boolean = false,
) {
    override fun toString() = "StickerSetItem(id=$id, count=$count, installed=$isInstalled, busy=$isBusy)"
}

/** What a set's menu asks for: its stickers shown, its link shared, or the set archived or (after a question) removed. */
internal class StickerSetActions(
    val onOpen: (StickerSetItem) -> Unit,
    val onShare: (StickerSetItem) -> Unit,
    val onArchive: (StickerSetItem) -> Unit,
    val onRemove: (StickerSetItem) -> Unit,
)

/** A [StickerSetSummary] as a row of a list shows it. */
internal fun StickerSetSummary.toItem(isBusy: Boolean = false) =
    StickerSetItem(
        id = id,
        title = title,
        name = name,
        count = size,
        cover = cover,
        isInstalled = isInstalled,
        isBusy = isBusy,
    )

/** What the user does on Settings → Stickers, handled by [StickersViewModel.onEvent]. */
sealed interface StickersEvent {
    /** Load the sets again after they failed. */
    data object Retry : StickersEvent

    /** The sets were dragged into the order of [setIds]. */
    data class Reorder(
        val setIds: List<Long>,
    ) : StickersEvent

    /** Move set [id] one place up or down: screen readers' "Move up" and "Move down". */
    data class Move(
        val id: Long,
        val up: Boolean,
    ) : StickersEvent

    /** Archive set [id] (its menu's "Archive"). */
    data class Archive(
        val id: Long,
    ) : StickersEvent

    /** Remove set [id] (its menu's "Remove", once confirmed). */
    data class Remove(
        val id: Long,
    ) : StickersEvent

    /** Put back set [id], archived or removed a moment ago, at the place it had ([previousOrder]). */
    data class Undo(
        val id: Long,
        val previousOrder: List<Long>,
    ) : StickersEvent
}

/** What Settings → Stickers does once. */
sealed interface StickersEffect {
    /** A failure the page shows in a snackbar. */
    data class ShowError(
        val error: DomainError,
    ) : StickersEffect

    /**
     * Set [id] named [title] was archived ([archived]) or removed: a snackbar that offers to undo it, putting it back
     * in [previousOrder].
     */
    data class Changed(
        val id: Long,
        val title: String,
        val archived: Boolean,
        val previousOrder: List<Long>,
    ) : StickersEffect {
        override fun toString() = "Changed(id=$id, archived=$archived)"
    }
}
