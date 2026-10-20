package dev.kyralabs.pingogram.feature.chat.ui.content

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

// The pieces photos, videos, stickers and video messages are built from: a TDLib file loaded by Coil and faded in,
// the blurred minithumbnail under it while it loads, and the labels and badges drawn over media.

/**
 * How far the TDLib image of [key] has faded in: 0 while it loads, 1 once it shows. Starts over for another key, e.g.
 * when a list item shows another message. Pass it to [TdlibImage], and fade placeholders out with it in the draw
 * phase.
 */
@Composable
internal fun rememberImageAlpha(key: Any): Animatable<Float, AnimationVector1D> = remember(key) { Animatable(0f) }

/**
 * TDLib file [fileId] as an image, decoded by Coil at [widthPx] × [heightPx] (`docs/PERFORMANCE.md` §4; the fetcher
 * downloads it at visible priority). It fades in through [alpha] on the motion scheme's default effects spring once
 * it is loaded from disk, and shows at once when Coil's memory cache has it, so lists do not flicker while scrolling.
 * [colorFilter] tints it, e.g. the dark theme's dim of photos ([pingogramColors]' `mediaColorFilter`). Decorative: the
 * component around it describes it.
 */
@Composable
internal fun TdlibImage(
    fileId: Int,
    widthPx: Int,
    heightPx: Int,
    alpha: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
) {
    val context = LocalPlatformContext.current
    val request =
        remember(context, fileId, widthPx, heightPx) {
            ImageRequest
                .Builder(context)
                .data(TdlibFileImage(fileId))
                .size(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1))
                .build()
        }
    val scope = rememberCoroutineScope()
    val fadeIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier.graphicsLayer { this.alpha = alpha.value },
        contentScale = contentScale,
        colorFilter = colorFilter,
        onSuccess = { state ->
            // Coil's own crossfade is a tween (DESIGN_SYSTEM.md §5), so the fade is ours, as in core:ui's Avatar.
            val loaded = state.result.dataSource
            if (loaded == DataSource.DISK || loaded == DataSource.NETWORK) {
                scope.launch { alpha.animateTo(1f, fadeIn) }
            } else {
                scope.launch(start = CoroutineStart.UNDISPATCHED) { alpha.snapTo(1f) }
            }
        },
    )
}

/** Fades this content out as an image over it fades in through [imageAlpha]; read in the draw phase. */
internal fun Modifier.fadingOutAs(imageAlpha: Animatable<Float, AnimationVector1D>): Modifier =
    graphicsLayer { alpha = 1f - imageAlpha.value }

/**
 * What a photo or video shows until its image is loaded, and instead of it while it is hidden (spoilers,
 * self-destructing media): the [minithumbnail] blurred, cropped to fill [displaySize], tinted by [colorFilter] as the
 * image over it is. The blur needs Android 12; before that, and without a minithumbnail, it is a tonal placeholder in
 * `surfaceContainerHighest`. The minithumbnail is decoded once per photo (it is about 40 px wide).
 */
@Composable
internal fun MinithumbnailPlaceholder(
    minithumbnail: Minithumbnail?,
    displaySize: DpSize,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
) {
    val bitmap =
        if (minithumbnail != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            remember(minithumbnail) { decodeMinithumbnail(minithumbnail) }
        } else {
            null
        }
    if (bitmap == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.blur(minithumbnailBlur(bitmap, displaySize), BlurredEdgeTreatment.Rectangle),
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
        )
    }
}

private fun decodeMinithumbnail(minithumbnail: Minithumbnail): ImageBitmap? =
    BitmapFactory.decodeByteArray(minithumbnail.jpeg, 0, minithumbnail.jpeg.size)?.asImageBitmap()

/** A blur of [MINITHUMBNAIL_BLUR_PIXELS] pixels of [bitmap] at the scale that crops it to [displaySize]. */
private fun minithumbnailBlur(
    bitmap: ImageBitmap,
    displaySize: DpSize,
): Dp {
    val scale =
        maxOf(
            displaySize.width.value / bitmap.width.coerceAtLeast(1),
            displaySize.height.value / bitmap.height.coerceAtLeast(1),
        )
    return Dp(scale * MINITHUMBNAIL_BLUR_PIXELS)
}

// Enough to hide the JPEG blocks of a 40 px image, little enough to keep its shapes.
private const val MINITHUMBNAIL_BLUR_PIXELS = 1.5f

/**
 * A label drawn over media, e.g. a video's length: `labelSmall` in `onMediaOverlay` on a pill of `mediaOverlay`, so it
 * stays legible on any picture. With [dot], a dot follows the text in the pill, e.g. for a video message nobody has
 * watched yet.
 */
@Composable
internal fun MediaLabel(
    text: String,
    modifier: Modifier = Modifier,
    dot: Boolean = false,
) {
    val colors = MaterialTheme.pingogramColors
    Row(
        modifier =
            modifier
                .background(colors.mediaOverlay, CircleShape)
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, color = colors.onMediaOverlay, maxLines = 1, style = MaterialTheme.typography.labelSmall)
        if (dot) Badge(containerColor = colors.onMediaOverlay)
    }
}

/** The round badge on a video's thumbnail that says it plays: a play icon on `mediaOverlay`. Decorative. */
@Composable
internal fun PlayBadge(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.pingogramColors
    Box(
        modifier = modifier.size(MaterialTheme.sizes.playBadge).background(colors.mediaOverlay, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_play_arrow_filled),
            contentDescription = null,
            tint = colors.onMediaOverlay,
        )
    }
}

/**
 * The badge on a GIF's thumbnail: "GIF" in `labelLargeEmphasized` on `mediaOverlay`, round like [PlayBadge], and a
 * pill when large fonts make the text wider. Decorative.
 */
@Composable
internal fun GifBadge(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.pingogramColors
    val side = MaterialTheme.sizes.playBadge
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = side, minHeight = side)
                .background(colors.mediaOverlay, CircleShape)
                .padding(horizontal = MaterialTheme.spacing.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(UiR.string.preview_gif),
            color = colors.onMediaOverlay,
            maxLines = 1,
            style = MaterialTheme.typography.labelLargeEmphasized,
        )
    }
}
