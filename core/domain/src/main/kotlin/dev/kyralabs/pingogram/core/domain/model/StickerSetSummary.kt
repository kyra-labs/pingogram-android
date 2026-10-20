package dev.kyralabs.pingogram.core.domain.model

/**
 * A sticker set the user installed (`REQUIREMENTS.md` FR-C5, the sticker panel), without its stickers: its [title], how
 * many stickers it has ([size]) and the sticker that stands for it ([cover], the first one; null while unknown).
 * `StickerRepository.stickerSet` loads its stickers.
 */
data class StickerSetSummary(
    val id: Long,
    val title: String,
    val size: Int,
    val cover: Sticker?,
)
