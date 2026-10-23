package dev.kyralabs.pingogram.core.domain.model

/**
 * A custom emoji: the picture that stands for an emoji in a text (td_api.tl `textEntityTypeCustomEmoji`), a sticker
 * of a custom emoji set (`stickerFullTypeCustomEmoji`), drawn in the text's size in place of the emoji the text holds
 * for it (`sticker.emoji` is that emoji too). An animated (TGS) sticker plays; a still (WebP) one shows as it is; a
 * video (WebM) one shows its thumbnail.
 *
 * @property id the custom emoji's id, as the entity names it.
 * @property needsRepainting it is a single color picture meant to take the color of the text around it: drawn tinted
 *   with that color, as Telegram's apps do.
 */
data class CustomEmoji(
    val id: Long,
    val sticker: Sticker,
    val needsRepainting: Boolean,
)
