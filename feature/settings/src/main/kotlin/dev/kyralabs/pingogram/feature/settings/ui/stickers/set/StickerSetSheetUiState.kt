package dev.kyralabs.pingogram.feature.settings.ui.stickers.set

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Sticker
import kotlinx.collections.immutable.ImmutableList

/** What the sheet of a sticker set shows (`REQUIREMENTS.md` FR-C5): a link's set, or one tapped in Settings. */
@Immutable
sealed interface StickerSetSheetUiState {
    data object Loading : StickerSetSheetUiState

    /** The set could not be shown: [error]; [canRetry] where trying again may help (no network, a flood wait). */
    data class Failed(
        val error: DomainError,
        val canRetry: Boolean,
    ) : StickerSetSheetUiState

    /**
     * The set: its [title] (user content), its short [name] for its link, its [stickers] in their order; whether the
     * user has it ([isInstalled]) or archived it ([isArchived]); [isChanging] while Telegram is asked to add or remove
     * it, and why that failed last ([changeError]).
     */
    data class Content(
        val title: String,
        val name: String,
        val stickers: ImmutableList<Sticker>,
        val isInstalled: Boolean,
        val isArchived: Boolean,
        val isChanging: Boolean = false,
        val changeError: DomainError? = null,
    ) : StickerSetSheetUiState {
        override fun toString() =
            "Content(stickers=${stickers.size}, installed=$isInstalled, archived=$isArchived, changing=$isChanging)"
    }
}

/** What the user does on the sheet, handled by [StickerSetSheetViewModel.onEvent]. */
sealed interface StickerSetSheetEvent {
    data object Retry : StickerSetSheetEvent

    /** "Add stickers": installs the set (an archived one comes back too). */
    data object Add : StickerSetSheetEvent

    /** "Remove": takes the set out of the user's sets. */
    data object Remove : StickerSetSheetEvent
}
