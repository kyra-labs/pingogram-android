package dev.kyralabs.pingogram.feature.settings.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.feature.settings.ui.stickers.set.StickerSetSheetUiState
import kotlinx.collections.immutable.toImmutableList

/** The states of a sticker set's sheet, for previews and screenshot tests. */
internal object StickerSetSheetPreviewStates {
    private val stickers =
        listOf("🐝", "🌻", "🐞", "🦋", "🌷", "🐛", "🍯", "🌼", "🐌", "🍄", "🌿", "🐸")
            .mapIndexed { index, emoji ->
                // No file reaches previews: the emoji stands in for the picture.
                Sticker(SET_ID, STICKER_SIDE, STICKER_SIDE, emoji, StickerFormat.Webp, null, FileRef(index + 1, 0))
            }.toImmutableList()

    val notAdded =
        StickerSetSheetUiState.Content(
            title = "Garden friends",
            name = "garden_friends",
            stickers = stickers,
            isInstalled = false,
            isArchived = false,
        )

    val added = notAdded.copy(isInstalled = true)

    val adding = notAdded.copy(isChanging = true)

    val addFailed = notAdded.copy(changeError = DomainError.Network)

    val notFound = StickerSetSheetUiState.Failed(DomainError.NotFound, canRetry = false)

    val offline = StickerSetSheetUiState.Failed(DomainError.Network, canRetry = true)
}

private const val SET_ID = 1L

/** Telegram's stickers are 512 px on their longer side. */
private const val STICKER_SIDE = 512
