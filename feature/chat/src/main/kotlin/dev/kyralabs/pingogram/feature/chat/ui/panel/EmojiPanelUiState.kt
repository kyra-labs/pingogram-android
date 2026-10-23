package dev.kyralabs.pingogram.feature.chat.ui.panel

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.EmojiCategory
import dev.kyralabs.pingogram.core.domain.model.Sticker
import kotlinx.collections.immutable.ImmutableList

/**
 * What the composer's emoji and sticker panel shows (`REQUIREMENTS.md` FR-C5): its [emoji] tab and its [stickers] tab,
 * each loading until the panel first opens.
 */
@Immutable
data class EmojiPanelUiState(
    val emoji: EmojiTab = EmojiTab.Loading,
    val stickers: StickerTab = StickerTab.Loading,
)

/** The emoji tab. */
@Immutable
sealed interface EmojiTab {
    /** Reading the catalog. */
    data object Loading : EmojiTab

    /** The emoji in [sections]: the recent ones first, when there are any, then Unicode's groups. */
    data class Content(
        val sections: ImmutableList<EmojiSection>,
    ) : EmojiTab
}

/** One section of the emoji tab: the recent emoji ([category] null), or the emoji of one [category]. */
@Immutable
data class EmojiSection(
    val category: EmojiCategory?,
    val emoji: ImmutableList<String>,
)

/** The stickers tab. */
@Immutable
sealed interface StickerTab {
    /** Reading the lists of stickers. */
    data object Loading : StickerTab

    /** The installed sticker sets could not be read: [error], with "Try again". */
    data class Error(
        val error: DomainError,
    ) : StickerTab

    /** The account has no favorite, recent or installed stickers. */
    data object Empty : StickerTab

    /** The stickers in [sections]: favorites, recent ones, then each installed set in the user's order. */
    data class Content(
        val sections: ImmutableList<StickerSection>,
    ) : StickerTab
}

/**
 * One section of the stickers tab, by a [key] that stays while it shows: its [stickers], and [placeholders] cells for
 * those of a set not loaded yet (the set's size), so the grid keeps its length when they come.
 */
@Immutable
data class StickerSection(
    val key: String,
    val kind: StickerSectionKind,
    val stickers: ImmutableList<Sticker>,
    val placeholders: Int = 0,
)

/** What a [StickerSection] holds. */
@Immutable
sealed interface StickerSectionKind {
    /** The user's favorite stickers. */
    data object Favorites : StickerSectionKind

    /** The stickers the user sent last. */
    data object Recent : StickerSectionKind

    /** Installed sticker set [setId], named [title], shown by its [cover] in the row of sets. */
    data class Set(
        val setId: Long,
        val title: String,
        val cover: Sticker?,
    ) : StickerSectionKind
}
