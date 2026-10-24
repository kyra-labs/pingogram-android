package dev.kyralabs.pingogram.core.domain.model

/**
 * A sticker set without its stickers (`REQUIREMENTS.md` FR-C5: the sticker panel, Settings → Stickers): its [title],
 * its short [name] (`t.me/addstickers/<name>`), how many stickers it has ([size]) and the sticker that stands for it
 * ([cover], the first one; null while unknown). [isInstalled] and [isArchived] say whether the user installed or
 * archived it, [isOfficial] whether Telegram made it, [isViewed] whether a trending set was seen already.
 * `StickerRepository.stickerSet` loads its stickers.
 */
data class StickerSetSummary(
    val id: Long,
    val title: String,
    val size: Int,
    val cover: Sticker?,
    val name: String = "",
    val isInstalled: Boolean = true,
    val isArchived: Boolean = false,
    val isOfficial: Boolean = false,
    val isViewed: Boolean = true,
) {
    override fun toString() = "StickerSetSummary(id=$id, size=$size, installed=$isInstalled, archived=$isArchived)"
}

/** A sticker set with its [stickers], in their order, as [StickerSetSummary.cover] and its flags describe it. */
data class StickerSetDetails(
    val set: StickerSetSummary,
    val stickers: List<Sticker>,
)
