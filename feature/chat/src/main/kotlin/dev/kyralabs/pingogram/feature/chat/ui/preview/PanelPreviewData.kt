package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.EmojiCategory
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanelUiState
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiSection
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiTab
import dev.kyralabs.pingogram.feature.chat.ui.panel.StickerSection
import dev.kyralabs.pingogram.feature.chat.ui.panel.StickerSectionKind
import dev.kyralabs.pingogram.feature.chat.ui.panel.StickerTab
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Sample states of the emoji and sticker panel for previews and screenshot tests. The stickers have no file on the
 * device, so they show their emoji, as real ones do until they are downloaded.
 */
internal object PanelPreviewData {
    private fun sticker(
        id: Int,
        emoji: String,
    ) = Sticker(
        setId = 1,
        width = STICKER_SIDE,
        height = STICKER_SIDE,
        emoji = emoji,
        format = StickerFormat.Tgs,
        thumbnail = null,
        file = FileRef(id, 0),
    )

    private fun stickers(
        firstId: Int,
        emoji: String,
    ) = emoji.split(' ').mapIndexed { index, glyph -> sticker(firstId + index, glyph) }.toImmutableList()

    private val emoji =
        EmojiTab.Content(
            persistentListOf(
                EmojiSection(category = null, emoji = persistentListOf("😂", "❤️", "👍", "🙏", "🎉")),
                EmojiSection(
                    EmojiCategory.SmileysAndEmotion,
                    "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 🫠 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 🥲 😋 😛 😜 🤪 😝 🤑 🤗 🤭"
                        .split(' ')
                        .toImmutableList(),
                ),
                EmojiSection(EmojiCategory.AnimalsAndNature, "🐵 🐒 🦍 🦧 🐶 🐕 🦮 🐩".split(' ').toImmutableList()),
            ),
        )

    private val stickers =
        StickerTab.Content(
            persistentListOf(
                StickerSection("recent", StickerSectionKind.Recent, stickers(RECENT_IDS, "🦆 🐱 🌻 🎈")),
                StickerSection(
                    key = "set:1",
                    kind = StickerSectionKind.Set(1, "Garden friends", sticker(COVER_ID, "🐝")),
                    stickers = stickers(SET_IDS, "🐝 🐞 🦋 🐌 🐛 🌷 🌼 🍄"),
                ),
                StickerSection(
                    key = "set:2",
                    kind = StickerSectionKind.Set(2, "Weather", cover = null),
                    stickers = persistentListOf(),
                    placeholders = 5,
                ),
            ),
        )

    /** Both tabs with content: recent emoji, two categories; recent stickers, a loaded set and one loading. */
    val content = EmojiPanelUiState(emoji = emoji, stickers = stickers)

    /** The installed sets could not be read. */
    val stickersError = EmojiPanelUiState(emoji = emoji, stickers = StickerTab.Error(DomainError.Network))

    /** The account has no stickers. */
    val stickersEmpty = EmojiPanelUiState(emoji = emoji, stickers = StickerTab.Empty)

    private const val STICKER_SIDE = 512
    private const val RECENT_IDS = 9_100
    private const val SET_IDS = 9_200
    private const val COVER_ID = 9_300
}
