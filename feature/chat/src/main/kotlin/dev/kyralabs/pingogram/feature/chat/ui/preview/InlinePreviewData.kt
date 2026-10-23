package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.Audio
import dev.kyralabs.pingogram.core.domain.model.Contact
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.InlineResult
import dev.kyralabs.pingogram.core.domain.model.InlineResultsButton
import dev.kyralabs.pingogram.core.domain.model.InlineResultsButtonType
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.domain.model.ThumbnailFormat
import dev.kyralabs.pingogram.core.domain.model.Venue
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineBotUiState
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineCellSize
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineLocationSheet
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlinePage
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineResultEntry
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineResultsContent
import kotlinx.collections.immutable.toImmutableList

/**
 * Inline bots for previews and screenshot tests: @gif's GIFs for "cats" (and its suggestions under the placeholder),
 * stickers, a places bot's venues with its "Start" button, a mix of rows, and the loading, empty and failed states, the
 * next page loading, and the question whether to share the location.
 */
internal object InlinePreviewData {
    private const val QUERY_ID = 1L
    private const val GIF_FILE_ID = 5_000
    private const val THUMBNAIL_FILE_ID = 5_100
    private const val GIF_COUNT = 9
    private const val GIF_WIDTH = 320
    private const val GIF_HEIGHT = 240
    private const val GIF_SECONDS = 3
    private const val SONG_SECONDS = 214
    private const val FLOOD_WAIT_SECONDS = 42L

    private val gifs =
        (0 until GIF_COUNT)
            .map { index ->
                InlineResult.Animation(
                    id = "gif$index",
                    animation =
                        Animation(
                            duration = GIF_SECONDS,
                            width = GIF_WIDTH,
                            height = GIF_HEIGHT,
                            mimeType = "video/mp4",
                            minithumbnail = null,
                            thumbnail = thumbnail(GIF_FILE_ID + index),
                            file = FileRef(GIF_FILE_ID + GIF_COUNT + index, 0),
                        ),
                    title = "Cat $index",
                )
            }.entries()

    private val stickers =
        listOf("😺", "😸", "😹", "😻", "😼", "😽")
            .mapIndexed { index, emoji ->
                InlineResult.Sticker("sticker$index", ContentPreviewData.sticker(StickerFormat.Webp, emoji).sticker)
            }.entries()

    private val places =
        listOf(
            InlineResult.Venue(
                "v1",
                Venue(Location(52.52, 13.405), "Café Einstein", "Kurfürstenstraße 58, Berlin"),
                thumbnail = null,
            ),
            InlineResult.Venue(
                "v2",
                Venue(Location(52.51, 13.39), "Bonanza Coffee", "Oderberger Str. 35, Berlin"),
                thumbnail = null,
            ),
            InlineResult.Venue(
                "v3",
                Venue(Location(52.50, 13.42), "The Barn", "Schönhauser Allee 8, Berlin"),
                thumbnail = null,
            ),
        ).entries()

    private val rows =
        listOf(
            InlineResult.Article(
                "a1",
                url = "https://en.wikipedia.org/wiki/Cat",
                title = "Cat",
                description = "The cat is a small domesticated carnivorous mammal.",
                thumbnail = thumbnail(THUMBNAIL_FILE_ID),
            ),
            InlineResult.Article("a2", "https://en.wikipedia.org/wiki/Kitten", "Kitten", "A juvenile cat.", null),
            InlineResult.Contact("c1", Contact("+15550100", "Ada", "Lovelace", 0), thumbnail = null),
            InlineResult.Audio(
                "s1",
                Audio(
                    duration = SONG_SECONDS,
                    title = "Cat Song",
                    performer = "The Whiskers",
                    fileName = "cat_song.mp3",
                    albumCoverMinithumbnail = null,
                    albumCoverThumbnail = null,
                    file = FileRef(THUMBNAIL_FILE_ID + 1, 0),
                ),
            ),
            InlineResult.Game("g1", "Cat Run", "Jump over the boxes", photo = null),
        ).entries()

    /** @gif's GIFs for "cats", more to come. */
    val gifResults =
        InlineBotUiState(
            username = "gif",
            placeholder = "Search GIFs…",
            query = "cats",
            content = InlineResultsContent.Results(gifs, null, InlineCellSize.Media, InlinePage.More),
        )

    /** "@gif " alone: the bot's suggestions under its placeholder in the field. */
    val gifSuggestions = gifResults.copy(query = "")

    /** Stickers for a cat, in the sticker cells. */
    val stickerResults =
        InlineBotUiState(
            username = "stickers",
            placeholder = "Search stickers",
            query = "cat",
            content = InlineResultsContent.Results(stickers, null, InlineCellSize.Stickers, InlinePage.Done),
        )

    /** The places bot's "Start" button above its venues. */
    val placeResults =
        InlineBotUiState(
            username = "places_bot",
            placeholder = "Find a place",
            query = "coffee",
            content =
                InlineResultsContent.Results(
                    places,
                    InlineResultsButton("Sign in to save places", InlineResultsButtonType.StartBot("login")),
                    InlineCellSize.Media,
                    InlinePage.Done,
                ),
        )

    /** Articles, a contact, a song and a game, as rows; the next page loading, a keyboard's pick on the second. */
    val rowResults =
        InlineBotUiState(
            username = "wiki",
            placeholder = "Search Wikipedia",
            query = "cat",
            content = InlineResultsContent.Results(rows, null, InlineCellSize.Media, InlinePage.Loading),
        )

    val loading = gifResults.copy(content = InlineResultsContent.Loading)

    val empty = gifResults.copy(query = "zzzz", content = InlineResultsContent.Empty(button = null))

    val floodWait = gifResults.copy(content = InlineResultsContent.Failed(DomainError.FloodWait(FLOOD_WAIT_SECONDS)))

    val failed = gifResults.copy(content = InlineResultsContent.Failed(DomainError.Network))

    /** The question before the places bot's first query, with Android's permission to ask for first. */
    val locationSheet = InlineLocationSheet(username = "places_bot", needsPermission = true)

    private fun thumbnail(fileId: Int) = Thumbnail(ThumbnailFormat.Jpeg, GIF_WIDTH, GIF_HEIGHT, FileRef(fileId, 0))

    private fun List<InlineResult>.entries() = map { InlineResultEntry(QUERY_ID, it) }.toImmutableList()
}
