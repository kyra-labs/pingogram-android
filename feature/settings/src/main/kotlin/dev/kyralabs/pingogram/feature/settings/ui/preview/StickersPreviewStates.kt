package dev.kyralabs.pingogram.feature.settings.ui.preview

import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.domain.model.StickerSetKind
import dev.kyralabs.pingogram.feature.settings.ui.stickers.StickerSetItem
import dev.kyralabs.pingogram.feature.settings.ui.stickers.StickerSetListUiState
import dev.kyralabs.pingogram.feature.settings.ui.stickers.StickersUiState
import kotlinx.collections.immutable.persistentListOf

/** The states of Settings → Stickers, its Emoji page and their lists, for previews and screenshot tests. */
internal object StickersPreviewStates {
    private fun set(
        id: Long,
        title: String,
        emoji: String,
        count: Int,
        isInstalled: Boolean = true,
        isBusy: Boolean = false,
        kind: StickerSetKind = StickerSetKind.Stickers,
    ) = StickerSetItem(
        id = id,
        title = title,
        name = "set$id",
        count = count,
        // No file reaches previews: the emoji stands in for the picture.
        cover = Sticker(id, STICKER_SIDE, STICKER_SIDE, emoji, StickerFormat.Webp, null, FileRef(id.toInt(), 0)),
        isInstalled = isInstalled,
        isBusy = isBusy,
        kind = kind,
    )

    val content =
        StickersUiState.Content(
            installed =
                persistentListOf(
                    set(1, "Garden friends", "🐝", 24),
                    set(2, "Morning coffee", "☕", 18),
                    set(3, "Cats at work", "🐱", 30),
                    set(4, "Weather report", "🌦️", 12),
                ),
            archivedCount = 3,
            newTrendingCount = 2,
        )

    val noSets = StickersUiState.Content(installed = persistentListOf(), archivedCount = 0, newTrendingCount = 0)

    val trending =
        StickerSetListUiState.Content(
            persistentListOf(
                set(11, "Space dogs", "🐶", 20, isInstalled = false),
                set(12, "Tiny dinosaurs", "🦖", 16, isInstalled = false, isBusy = true),
                set(13, "Fruit salad", "🍓", 24),
                set(14, "Night owls", "🦉", 14, isInstalled = false),
            ),
        )

    val archived =
        StickerSetListUiState.Content(
            persistentListOf(
                set(21, "Holiday lights", "🎄", 22, isInstalled = false),
                set(22, "Old memes", "😂", 40, isInstalled = false),
            ),
        )

    val noArchived = StickerSetListUiState.Content(persistentListOf())

    /** The Emoji page: the user's custom emoji sets. */
    val emoji =
        StickersUiState.Content(
            installed =
                persistentListOf(
                    set(31, "Animated hearts", "❤️", 40, kind = StickerSetKind.Emoji),
                    set(32, "Tiny flags", "🏳️", 120, kind = StickerSetKind.Emoji),
                    set(33, "Pixel faces", "🙂", 64, kind = StickerSetKind.Emoji),
                ),
            archivedCount = 1,
            newTrendingCount = 4,
        )

    val trendingEmoji =
        StickerSetListUiState.Content(
            persistentListOf(
                set(41, "Party time", "🎉", 48, isInstalled = false, kind = StickerSetKind.Emoji),
                set(42, "Weather icons", "⛅", 36, isInstalled = false, kind = StickerSetKind.Emoji),
                set(31, "Animated hearts", "❤️", 40, kind = StickerSetKind.Emoji),
            ),
        )
}

/** Telegram's stickers are 512 px on their longer side. */
private const val STICKER_SIDE = 512
