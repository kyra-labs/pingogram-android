package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.EmojiCategory
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanelUiState
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiSection
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiTab
import dev.kyralabs.pingogram.feature.chat.ui.panel.GifSearch
import dev.kyralabs.pingogram.feature.chat.ui.panel.GifTab
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

    private val gifs = (1..GIF_COUNT).map { gif(GIF_IDS + it) }.toImmutableList()

    /** The GIF tab with saved GIFs, search offered. */
    val gifsSaved = content.copy(gifs = GifTab.Content(saved = gifs, canSearch = true))

    /** The GIF tab without saved GIFs. */
    val gifsEmpty = content.copy(gifs = GifTab.Content(saved = persistentListOf(), canSearch = true))

    /** A search with results and its next page on its way. */
    val gifsSearch =
        content.copy(
            gifs =
                GifTab.Content(
                    saved = gifs,
                    canSearch = true,
                    search = GifSearch("cats", gifs, isLoading = true, hasMore = true),
                ),
        )

    /** A search that found nothing. */
    val gifsNoResults =
        content.copy(
            gifs = GifTab.Content(saved = gifs, canSearch = true, search = GifSearch("zzz", persistentListOf())),
        )

    /** The saved GIFs could not be read. */
    val gifsError = content.copy(gifs = GifTab.Error(DomainError.Network))

    // No file reaches previews: the blurred placeholder stands in for each GIF's picture.
    private fun gif(fileId: Int) =
        Animation(
            GIF_SECONDS,
            GIF_WIDTH,
            GIF_HEIGHT,
            "video/mp4",
            minithumbnail = null,
            thumbnail = null,
            file = FileRef(fileId, 0),
        )

    private const val STICKER_SIDE = 512
    private const val GIF_COUNT = 7
    private const val GIF_SECONDS = 2
    private const val GIF_WIDTH = 320
    private const val GIF_HEIGHT = 240
    private const val GIF_IDS = 9_400
    private const val RECENT_IDS = 9_100
    private const val SET_IDS = 9_200
    private const val COVER_ID = 9_300
}
