package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.testing.TdApiFixtures.file
import org.drinkless.tdlib.TdApi

/** The sticker of custom emoji [customEmojiId], as `getCustomEmojiStickers` returns it. */
fun customEmojiSticker(
    customEmojiId: Long,
    format: TdApi.StickerFormat = TdApi.StickerFormatTgs(),
    emoji: String = "🎉",
    needsRepainting: Boolean = false,
    file: TdApi.File = file(id = customEmojiId.toInt()),
) = TdApi.Sticker(
    customEmojiId,
    8,
    100,
    100,
    emoji,
    format,
    TdApi.StickerFullTypeCustomEmoji(customEmojiId, needsRepainting),
    null,
    file,
)
