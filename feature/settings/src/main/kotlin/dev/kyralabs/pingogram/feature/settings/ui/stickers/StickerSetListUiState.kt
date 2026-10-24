package dev.kyralabs.pingogram.feature.settings.ui.stickers

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.ImmutableList

/** What Settings → Stickers' Trending stickers or Archived stickers page shows. */
@Immutable
sealed interface StickerSetListUiState {
    data object Loading : StickerSetListUiState

    /** The sets could not be loaded, because of [error]. */
    data class Error(
        val error: DomainError,
    ) : StickerSetListUiState

    /** The [sets], in Telegram's order. */
    data class Content(
        val sets: ImmutableList<StickerSetItem>,
    ) : StickerSetListUiState
}

/** What the user does on the page, handled by [StickerSetListViewModel.onEvent]. */
sealed interface StickerSetListEvent {
    /** Load the sets again after they failed. */
    data object Retry : StickerSetListEvent

    /** Install set [id] ("Add"). */
    data class Add(
        val id: Long,
    ) : StickerSetListEvent
}

/** What the page does once. */
sealed interface StickerSetListEffect {
    /** A failure the page shows in a snackbar. */
    data class ShowError(
        val error: DomainError,
    ) : StickerSetListEffect

    /** Set [title] was added to the user's sets: a snackbar. */
    data class Added(
        val title: String,
    ) : StickerSetListEffect {
        override fun toString() = "Added(…)"
    }
}
