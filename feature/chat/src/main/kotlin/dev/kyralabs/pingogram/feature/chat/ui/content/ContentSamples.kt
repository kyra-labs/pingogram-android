package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import coil3.ColorImage
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.domain.model.ExpiredMedia
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.spacing

/** Which bubbles a [ContentSample] is shown in. */
internal enum class SampleBubbles {
    Incoming,
    Outgoing,

    /** An incoming bubble above an outgoing one, to check both color sets. */
    Both,

    /** No bubble: bare content ([isBare]). */
    None,
}

/**
 * One state of a content component for previews and screenshot tests: [content] in sample [bubbles] (a `Surface` in
 * the bubble roles, padded unless the content is edge to edge), with Coil's preview images loaded or, with
 * [imagesLoading], still loading. [content] gets the bubble's colors and whether it is the outgoing one.
 */
internal class ContentSample(
    val name: String,
    val bubbles: SampleBubbles,
    val edgeToEdge: Boolean = false,
    val imagesLoading: Boolean = false,
    val content: @Composable (colors: BubbleColors, outgoing: Boolean) -> Unit,
)

/**
 * Every component of this package in its states, named for the screenshot goldens: photo loading and loaded, albums
 * of 2, 3, 4, 7 and 10, video, GIF, stickers, voice messages idle, playing and downloading, a video message, music,
 * files in their three download states, location, venue, contact, polls before and after voting, a quiz, a closed
 * poll, a die, an animated emoji, game and invoice, story and expired media.
 */
internal val ContentSamples: List<ContentSample> =
    listOf(
        ContentSample("photoLoading", SampleBubbles.Incoming, edgeToEdge = true, imagesLoading = true) { _, _ ->
            MessagePhoto(ContentPreviewData.photo(rememberSampleMinithumbnail()), onOpen = {})
        },
        ContentSample("photo", SampleBubbles.Incoming, edgeToEdge = true) { _, _ ->
            MessagePhoto(ContentPreviewData.photo(rememberSampleMinithumbnail()), onOpen = {})
        },
        albumSample(2),
        albumSample(3),
        albumSample(4),
        albumSample(7),
        albumSample(10),
        ContentSample("video", SampleBubbles.Incoming, edgeToEdge = true) { _, _ ->
            MessageVideo(ContentPreviewData.video(rememberSampleMinithumbnail()), onOpen = {})
        },
        ContentSample("gif", SampleBubbles.Outgoing, edgeToEdge = true) { _, _ ->
            MessageAnimation(ContentPreviewData.animation(rememberSampleMinithumbnail()), onOpen = {})
        },
        ContentSample("sticker", SampleBubbles.None) { _, _ ->
            MessageSticker(ContentPreviewData.sticker(StickerFormat.Webp))
        },
        ContentSample("stickerAnimated", SampleBubbles.None) { _, _ ->
            MessageSticker(ContentPreviewData.sticker(StickerFormat.Tgs))
        },
        ContentSample("voice", SampleBubbles.Both) { colors, outgoing ->
            MessageVoiceNote(
                content = ContentPreviewData.voiceNote(isListened = outgoing),
                fileState = null,
                playback = Playback.Stopped,
                onPlayPause = {},
                onSeek = {},
                colors = colors,
            )
        },
        ContentSample("voicePlaying", SampleBubbles.Both) { colors, outgoing ->
            MessageVoiceNote(
                content = ContentPreviewData.voiceNote(isListened = true),
                fileState = null,
                playback =
                    Playback(isPlaying = !outgoing, progress = { if (outgoing) PAUSED_PROGRESS else PLAYING_PROGRESS }),
                onPlayPause = {},
                onSeek = {},
                colors = colors,
            )
        },
        ContentSample("voiceDownloading", SampleBubbles.Both) { colors, _ ->
            MessageVoiceNote(
                content = ContentPreviewData.voiceNote(isListened = false),
                fileState = downloading(DOWNLOADED_FRACTION),
                playback = Playback.Stopped,
                onPlayPause = {},
                onSeek = {},
                colors = colors,
            )
        },
        ContentSample("videoNote", SampleBubbles.None) { _, _ ->
            MessageVideoNote(ContentPreviewData.videoNote(rememberSampleMinithumbnail(), isViewed = false), onOpen = {})
        },
        ContentSample("audio", SampleBubbles.Both) { colors, outgoing ->
            MessageAudio(
                content = ContentPreviewData.audio,
                fileState = null,
                playback =
                    if (outgoing) {
                        Playback(
                            isPlaying = true,
                            progress = { PLAYING_PROGRESS },
                        )
                    } else {
                        Playback.Stopped
                    },
                onPlayPause = {},
                onSeek = {},
                colors = colors,
            )
        },
        documentSample("documentNotDownloaded", FileState.NotDownloaded(size = 0, canBeDownloaded = true)),
        documentSample("documentDownloading", downloading(DOWNLOADED_FRACTION)),
        documentSample("documentDownloaded", FileState.Downloaded(path = "", size = 0)),
        ContentSample("location", SampleBubbles.Both) { colors, outgoing ->
            val livePeriod = if (outgoing) MessageContent.Location.LIVE_PERIOD_FOREVER else 0
            MessageLocation(ContentPreviewData.location(livePeriod), onOpen = {}, colors = colors)
        },
        ContentSample("venue", SampleBubbles.Both) { colors, _ ->
            MessageVenue(ContentPreviewData.venue, onOpen = {}, colors = colors)
        },
        ContentSample("contact", SampleBubbles.Both) { colors, _ ->
            MessageContact(ContentPreviewData.contact, colors = colors)
        },
        ContentSample("pollBeforeVoting", SampleBubbles.Both) { colors, outgoing ->
            MessagePoll(ContentPreviewData.poll(voted = false, multipleAnswers = outgoing), colors = colors)
        },
        ContentSample("pollVoted", SampleBubbles.Both) { colors, outgoing ->
            MessagePoll(ContentPreviewData.poll(voted = true, anonymous = !outgoing), colors = colors)
        },
        ContentSample("quiz", SampleBubbles.Both) { colors, outgoing ->
            // Incoming: answered wrong (summer); outgoing: answered right (autumn).
            MessagePoll(
                ContentPreviewData.poll(voted = true, quiz = true, chosenIndex = if (outgoing) 2 else 1),
                colors = colors,
            )
        },
        ContentSample("pollClosed", SampleBubbles.Both) { colors, outgoing ->
            MessagePoll(ContentPreviewData.poll(voted = outgoing, closed = true), colors = colors)
        },
        ContentSample("dice", SampleBubbles.None) { _, _ ->
            MessageDice(MessageContent.Dice(emoji = "🎲", value = DICE_VALUE))
        },
        ContentSample("animatedEmoji", SampleBubbles.None) { _, _ ->
            MessageAnimatedEmoji(MessageContent.AnimatedEmoji(emoji = "🎉", sticker = null))
        },
        ContentSample("game", SampleBubbles.Both) { colors, outgoing ->
            if (outgoing) {
                MessageInvoice(MessageContent.Invoice("Concert tickets"), colors = colors)
            } else {
                MessageGame(MessageContent.Game("Lumberjack"), colors = colors)
            }
        },
        ContentSample("story", SampleBubbles.Both) { colors, _ -> MessageStory(colors = colors) },
        ContentSample("expired", SampleBubbles.Both) { colors, outgoing ->
            MessageExpired(
                MessageContent.Expired(if (outgoing) ExpiredMedia.VoiceNote else ExpiredMedia.Photo),
                colors = colors,
            )
        },
    )

private fun albumSample(count: Int) =
    ContentSample("album$count", SampleBubbles.Outgoing, edgeToEdge = true) { _, _ ->
        val minithumbnail = rememberSampleMinithumbnail()
        MediaAlbum(remember(minithumbnail) { ContentPreviewData.album(count, minithumbnail) }, onOpen = {})
    }

private fun documentSample(
    name: String,
    fileState: FileState,
) = ContentSample(name, SampleBubbles.Both) { colors, outgoing ->
    MessageDocument(
        content =
            if (outgoing) {
                ContentPreviewData.document(
                    "Quarterly report with a very long name, final version (2).pdf",
                    "application/pdf",
                )
            } else {
                ContentPreviewData.document("photos-from-the-trip.zip", "application/zip")
            },
        fileState = fileState,
        onDownload = {},
        onCancel = {},
        onOpen = {},
        colors = colors,
    )
}

private fun downloading(fraction: Float) =
    FileState.Downloading(
        downloadedSize = (SAMPLE_FILE_BYTES * fraction).toLong(),
        size = SAMPLE_FILE_BYTES,
        isActive = true,
    )

/**
 * [sample] in its bubbles, as in a conversation: incoming at the start, outgoing at the end, with the window margins.
 * Coil shows solid colors from the theme for images, or keeps them loading for [ContentSample.imagesLoading]; both
 * only in inspection mode (previews, screenshot tests).
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
internal fun ContentSampleFrame(
    sample: ContentSample,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val previewHandler =
        remember(colorScheme, sample.imagesLoading) {
            if (sample.imagesLoading) {
                // Keeps every image loading, so the placeholders show.
                object : AsyncImagePreviewHandler {
                    override suspend fun handle(
                        imageLoader: ImageLoader,
                        request: ImageRequest,
                    ): AsyncImagePainter.State = AsyncImagePainter.State.Loading(painter = null)
                }
            } else {
                // No primary or secondary container: in the brand schemes they are the outgoing bubble's color.
                val palette =
                    listOf(
                        colorScheme.tertiaryContainer,
                        colorScheme.secondary,
                        colorScheme.tertiary,
                        colorScheme.outline,
                        colorScheme.primary,
                    )
                AsyncImagePreviewHandler { request ->
                    val fileId = (request.data as? TdlibFileImage)?.fileId ?: 0
                    ColorImage(palette[fileId.mod(palette.size)].toArgb())
                }
            }
        }
    val bubbles = MaterialTheme.pingogramColors
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides previewHandler) {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.spacing.windowMarginCompact,
                        vertical = MaterialTheme.spacing.large,
                    ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            when (sample.bubbles) {
                SampleBubbles.Incoming -> {
                    SampleBubble(sample, bubbles.incomingBubble, outgoing = false)
                }

                SampleBubbles.Outgoing -> {
                    SampleBubble(sample, bubbles.outgoingBubble, outgoing = true)
                }

                SampleBubbles.Both -> {
                    SampleBubble(sample, bubbles.incomingBubble, outgoing = false)
                    SampleBubble(sample, bubbles.outgoingBubble, outgoing = true)
                }

                SampleBubbles.None -> {
                    sample.content(bubbles.incomingBubble, false)
                }
            }
        }
    }
}

@Composable
private fun SampleBubble(
    sample: ContentSample,
    colors: BubbleColors,
    outgoing: Boolean,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(color = colors.container, contentColor = colors.content, shape = MaterialTheme.shapes.large) {
            Box(if (sample.edgeToEdge) Modifier else Modifier.padding(MaterialTheme.spacing.medium)) {
                sample.content(colors, outgoing)
            }
        }
    }
}

/** The samples by name, for the previews. */
internal class ContentSampleProvider : PreviewParameterProvider<ContentSample> {
    override val values: Sequence<ContentSample> = ContentSamples.asSequence()

    override fun getDisplayName(index: Int): String = ContentSamples[index].name
}

@PingogramPreviews
@Composable
private fun MessageContentPreview(
    @PreviewParameter(ContentSampleProvider::class) sample: ContentSample,
) {
    PingogramPreview { ContentSampleFrame(sample) }
}

private const val SAMPLE_FILE_BYTES = 4_520_000L
private const val DOWNLOADED_FRACTION = 0.38f
private const val PLAYING_PROGRESS = 0.42f
private const val PAUSED_PROGRESS = 0.71f
private const val DICE_VALUE = 4
