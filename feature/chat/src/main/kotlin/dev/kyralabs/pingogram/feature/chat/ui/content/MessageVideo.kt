package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.domain.model.Video
import dev.kyralabs.pingogram.core.domain.model.VideoNote
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A video message without its caption (`REQUIREMENTS.md` FR-C4): its thumbnail edge to edge in its bubble
 * ([isEdgeToEdge]), sized like a photo ([mediaDisplaySize]) and clipped to [shape], with a [PlayBadge] in the middle
 * and its length and file size at the top start. Playback comes with the media viewer: tapping it calls [onOpen].
 * Spoilers and self-destructing videos show only the blurred minithumbnail.
 *
 * Screen readers hear "Video, 1:05, 12 MB".
 *
 * @param shape the clip, `large` by default; the bubble passes another one where its own corners differ.
 * @param maxWidth the widest the video gets: `mediaMaxWidth`, or less in a narrower bubble.
 */
@Composable
internal fun MessageVideo(
    content: MessageContent.Video,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    maxWidth: Dp = MaterialTheme.sizes.mediaMaxWidth,
) {
    val video = content.video
    val label =
        stringResource(
            R.string.content_duration_and_size,
            mediaDurationText(video.duration),
            fileSizeText(video.file.size),
        )
    VideoFrame(
        frame =
            VideoFrameModel(
                still = video.still(),
                hidden = content.hasSpoiler || content.isSecret,
                label = label,
                description =
                    stringResource(R.string.content_kind_and_details, stringResource(UiR.string.preview_video), label),
                isGif = false,
            ),
        onOpen = onOpen,
        modifier = modifier,
        shape = shape,
        maxWidth = maxWidth,
    )
}

/**
 * A GIF message without its caption (`REQUIREMENTS.md` FR-C4): like [MessageVideo], with a [GifBadge] in the middle
 * and its file size at the top start. GIFs will play inline; for now tapping it calls [onOpen].
 *
 * Screen readers hear "GIF, 1.2 MB".
 */
@Composable
internal fun MessageAnimation(
    content: MessageContent.Animation,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    maxWidth: Dp = MaterialTheme.sizes.mediaMaxWidth,
) {
    val animation = content.animation
    val label = fileSizeText(animation.file.size)
    VideoFrame(
        frame =
            VideoFrameModel(
                still = animation.still(),
                hidden = content.hasSpoiler || content.isSecret,
                label = label,
                description =
                    stringResource(R.string.content_kind_and_details, stringResource(UiR.string.preview_gif), label),
                isGif = true,
            ),
        onOpen = onOpen,
        modifier = modifier,
        shape = shape,
        maxWidth = maxWidth,
    )
}

/** What a video or GIF message shows: its [still], hidden or not, with [label] over it and [description] for it. */
private class VideoFrameModel(
    val still: VideoStill,
    val hidden: Boolean,
    val label: String,
    val description: String,
    val isGif: Boolean,
)

@Composable
private fun VideoFrame(
    frame: VideoFrameModel,
    onOpen: () -> Unit,
    modifier: Modifier,
    shape: Shape,
    maxWidth: Dp,
) {
    val sizes = MaterialTheme.sizes
    val still = frame.still
    val displaySize = mediaDisplaySize(still.width, still.height, maxWidth, sizes.mediaMaxHeight, sizes.mediaMinSize)
    Box(
        modifier =
            modifier
                .size(displaySize)
                .clip(shape)
                .clickable(onClickLabel = stringResource(R.string.content_open), onClick = onOpen)
                .semantics { contentDescription = frame.description },
    ) {
        VideoThumbnail(still, displaySize, frame.hidden, Modifier.matchParentSize())
        // The description says what the badges show.
        Box(Modifier.matchParentSize().clearAndSetSemantics {}) {
            if (frame.isGif) GifBadge(Modifier.align(Alignment.Center)) else PlayBadge(Modifier.align(Alignment.Center))
            MediaLabel(frame.label, Modifier.align(Alignment.TopStart).padding(MaterialTheme.spacing.small))
        }
    }
}

/** What a video, a GIF or a video message shows before it plays: its size in pixels and its stills. */
internal data class VideoStill(
    val width: Int,
    val height: Int,
    val minithumbnail: Minithumbnail?,
    val thumbnail: Thumbnail?,
)

internal fun Video.still() = VideoStill(width, height, minithumbnail, thumbnail)

internal fun Animation.still() = VideoStill(width, height, minithumbnail, thumbnail)

internal fun VideoNote.still() = VideoStill(length, length, minithumbnail, thumbnail)

/**
 * The [still] of a video, a GIF or a video message, cropped to fill [displaySize]: its blurred minithumbnail and over
 * it its thumbnail (when that is a picture, see [asPicture]), faded in once loaded. With [hidden], the minithumbnail
 * only. The caller sizes and clips it.
 */
@Composable
internal fun VideoThumbnail(
    still: VideoStill,
    displaySize: DpSize,
    hidden: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        MinithumbnailPlaceholder(still.minithumbnail, displaySize, Modifier.matchParentSize())
        val picture = still.thumbnail?.asPicture()
        if (!hidden && picture != null) {
            val density = LocalDensity.current
            TdlibImage(
                fileId = picture.file.id,
                widthPx = with(density) { displaySize.width.roundToPx() },
                heightPx = with(density) { displaySize.height.roundToPx() },
                alpha = rememberImageAlpha(picture.file.id),
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
