package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.toSize
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.domain.model.ThumbnailFormat
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.transition.sharedMediaViewerBounds
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.feature.media.ui.zoom.ZoomBounds
import dev.kyralabs.pingogram.feature.media.ui.zoom.ZoomState
import dev.kyralabs.pingogram.feature.media.ui.zoom.detectZoomGestures
import dev.kyralabs.pingogram.feature.media.ui.zoom.fittedSize
import kotlinx.collections.immutable.ImmutableMap

/**
 * One page of the viewer: [item]'s media fitted in the page, centered, on the screen's background.
 *
 * - **Photos**: the blurred minithumbnail, over it the largest smaller size already on the device (what the bubble
 *   showed), and over that the full size, faded in once loaded ([ViewerImage]); a determinate
 *   [CircularWavyProgressIndicator] while the full size downloads. With [zoom] (the shown page's), pinch to zoom,
 *   double tap to zoom in around the tap and out again on the motion scheme's default spatial spring, and drag to pan
 *   while zoomed ([ZoomState], drawn in the draw phase).
 * - **Videos and GIFs**: the blurred minithumbnail and the thumbnail, with the download's progress, until the file is
 *   on the device; once the item is in the player ([playback]), [videoSurface] under them, uncovered when the first
 *   frame shows. The screen stays on while a video plays.
 * - **Spoilers** ([MediaItem.isHidden]) show only the blurred minithumbnail and "Tap to show".
 * - **Container transform**: the fitted media shares its bounds with the bubble of the same message in the
 *   conversation ([sharedMediaViewerBounds]), growing out of it as the viewer opens and shrinking back into it as it
 *   closes, from exactly the corners the media shows in its bubble to square ones and back. Not while zoomed in: the
 *   zoomed photo fades out with the screen instead. The layers crop to the moving bounds, so a photo cropped in its
 *   bubble uncrops as it grows.
 *
 * A tap calls [onTap] (the screen toggles its chrome, or shows the spoiler); a double tap waits for the second tap only
 * where it zooms. Screen readers get no semantics from here: the pager describes the page.
 */
@Composable
internal fun MediaPage(
    item: MediaItem,
    files: ImmutableMap<Int, FileState>,
    playback: VideoPlayback?,
    onTap: () -> Unit,
    videoSurface: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    zoom: ZoomState? = null,
) {
    val media = item.media
    val zoomState = zoom?.takeIf { media is ViewerMedia.Image && !item.isHidden }
    val playing = playback?.isPlaying == true && media is ViewerMedia.Clip
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .then(if (playing) Modifier.keepScreenOn() else Modifier)
            .pageGestures(zoomState, onTap),
    ) {
        val container = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val fitted = fittedSize(media.width, media.height, container)
        val fittedDp = with(LocalDensity.current) { DpSize(fitted.width.toDp(), fitted.height.toDp()) }
        if (zoomState != null) SideEffect { zoomState.bounds = ZoomBounds(container, fitted) }
        val zoomed by remember(zoomState) { derivedStateOf { zoomState?.isZoomed == true } }
        Box(
            Modifier
                .align(Alignment.Center)
                .size(fittedDp)
                .sharedMediaViewerBounds(item.messageId, enabled = !zoomed)
                .zoomedBy(zoomState),
        ) {
            MediaLayers(item, files, playback, videoSurface, fittedDp)
        }
        if (item.isHidden) {
            SpoilerLabel(Modifier.align(Alignment.Center))
        } else if (playback?.showsFrame != true) {
            // A streamed video plays before its file is complete: the seek bar shows what came, not this.
            DownloadProgress(files[media.fileId], Modifier.align(Alignment.Center))
        }
    }
}

/**
 * Taps ([onTap]) and, with [zoom], double taps that zoom around the tap and pinches ([detectZoomGestures]). The taps
 * are outside the pinch, so they see the moves the pinch consumed and take no pan for a tap.
 */
@Composable
private fun Modifier.pageGestures(
    zoom: ZoomState?,
    onTap: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val currentOnTap by rememberUpdatedState(onTap)
    return pointerInput(zoom) {
        detectTapGestures(
            onTap = { currentOnTap() },
            onDoubleTap = zoom?.let { state -> { tap -> state.toggle(tap - size.toSize().center, spec, scope) } },
        )
    }.then(if (zoom != null) Modifier.pointerInput(zoom) { detectZoomGestures(zoom) } else Modifier)
}

/** Scaled and moved as [zoom] says, in the draw phase; unchanged without one. */
private fun Modifier.zoomedBy(zoom: ZoomState?): Modifier =
    if (zoom == null) {
        this
    } else {
        graphicsLayer {
            val current = zoom.zoom
            scaleX = current.scale
            scaleY = current.scale
            translationX = current.pan.x
            translationY = current.pan.y
        }
    }

/** What [item] shows in its fitted box of [displaySize]: the hidden spoiler's minithumbnail, or its media's layers. */
@Composable
private fun BoxScope.MediaLayers(
    item: MediaItem,
    files: ImmutableMap<Int, FileState>,
    playback: VideoPlayback?,
    videoSurface: @Composable (Modifier) -> Unit,
    displaySize: DpSize,
) {
    val media = item.media
    if (item.isHidden) {
        BlurredMinithumbnail(media.minithumbnail, displaySize, Modifier.matchParentSize())
    } else {
        when (media) {
            is ViewerMedia.Image -> PhotoLayers(media, files, displaySize)
            is ViewerMedia.Clip -> VideoLayers(media, media.thumbnail, displaySize, playback, videoSurface)
            is ViewerMedia.Gif -> VideoLayers(media, media.thumbnail, displaySize, playback, videoSurface)
        }
    }
}

/** The blurred minithumbnail, the largest smaller size already on the device, and the full size over them. */
@Composable
private fun BoxScope.PhotoLayers(
    media: ViewerMedia.Image,
    files: ImmutableMap<Int, FileState>,
    displaySize: DpSize,
) {
    BlurredMinithumbnail(media.minithumbnail, displaySize, Modifier.matchParentSize())
    val preview = remember(media, files) { previewSize(media, files) }
    if (preview != null) {
        ViewerImage(preview.file.id, preview.width, preview.height, Modifier.matchParentSize(), fadeIn = false)
    }
    ViewerImage(media.full.file.id, media.full.width, media.full.height, Modifier.matchParentSize())
}

/**
 * The surface once the item is in the player, covered by the blurred minithumbnail and the [thumbnail] until its first
 * frame shows: until then the surface may still hold the last frame of the item played before.
 */
@Composable
private fun BoxScope.VideoLayers(
    media: ViewerMedia,
    thumbnail: Thumbnail?,
    displaySize: DpSize,
    playback: VideoPlayback?,
    videoSurface: @Composable (Modifier) -> Unit,
) {
    if (playback != null) videoSurface(Modifier.matchParentSize())
    if (playback?.showsFrame != true) {
        BlurredMinithumbnail(media.minithumbnail, displaySize, Modifier.matchParentSize())
        val picture = thumbnail?.takeIf { it.isPicture }
        if (picture != null) ViewerImage(picture.file.id, picture.width, picture.height, Modifier.matchParentSize())
    }
}

/** A determinate wavy indicator of the download of [state], while it is not complete; nothing once it is or unknown. */
@Composable
private fun DownloadProgress(
    state: FileState?,
    modifier: Modifier = Modifier,
) {
    val progress =
        when (state) {
            is FileState.Downloading -> state.progress
            is FileState.NotDownloaded -> 0f
            is FileState.Downloaded, null -> return
        }
    val description = stringResource(R.string.media_downloading)
    Box(
        modifier =
            modifier
                .background(MaterialTheme.pingogramColors.mediaOverlay, CircleShape)
                .padding(MaterialTheme.spacing.small)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(WavyProgressIndicatorDefaults.CircularContainerSize),
        )
    }
}

/** "Tap to show" over a hidden spoiler, as a label on `mediaOverlay`. Its page's description says what it is. */
@Composable
private fun SpoilerLabel(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.pingogramColors
    Text(
        text = stringResource(R.string.media_spoiler_reveal),
        modifier =
            modifier
                .background(colors.mediaOverlay, CircleShape)
                .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small)
                .clearAndSetSemantics {},
        color = colors.onMediaOverlay,
        style = MaterialTheme.typography.labelLarge,
    )
}

/** The largest size of [media]'s photo other than the full one that is on the device; null if none is. */
private fun previewSize(
    media: ViewerMedia.Image,
    files: Map<Int, FileState>,
): PhotoSize? =
    media.photo.sizes
        .filter { it.file.id != media.full.file.id && files[it.file.id] is FileState.Downloaded }
        .maxByOrNull { it.width.toLong() * it.height }

/** JPEG, PNG, WebP and GIF thumbnails are pictures; MPEG4, WebM and TGS ones are animations Coil does not show. */
private val Thumbnail.isPicture: Boolean
    get() =
        when (format) {
            ThumbnailFormat.Jpeg, ThumbnailFormat.Png, ThumbnailFormat.Webp, ThumbnailFormat.Gif -> true
            ThumbnailFormat.Mpeg4, ThumbnailFormat.Webm, ThumbnailFormat.Tgs -> false
        }
