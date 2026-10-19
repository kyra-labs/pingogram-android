package dev.kyralabs.pingogram.feature.media.ui

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

// The images of the viewer's pages: TDLib files loaded by Coil, and the blurred minithumbnail under them. Like the
// conversation's (feature:chat MediaImage.kt), which features cannot share.

/**
 * TDLib file [fileId] as an image, decoded by Coil at [widthPx] × [heightPx] (`docs/PERFORMANCE.md` §4: explicit
 * sizes; the viewer's full sizes are decoded at their own size, so zooming shows their pixels), cropped to fill its
 * box: the page fits the box to the media, so nothing is cut off there, and while the box moves between the bubble and
 * the page (the container transform) the photo uncrops as it grows. The fetcher downloads it at visible priority if it
 * is not on the device. With [fadeIn], it fades in on the motion scheme's default effects
 * spring once loaded from disk (Coil's own crossfade is a tween); it shows at once from Coil's memory cache, and
 * without [fadeIn]. Decorative: the page describes it.
 */
@Composable
internal fun ViewerImage(
    fileId: Int,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
    fadeIn: Boolean = true,
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
    val alpha = remember(fileId) { Animatable(if (fadeIn) 0f else 1f) }
    val scope = rememberCoroutineScope()
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier.graphicsLayer { this.alpha = alpha.value },
        contentScale = ContentScale.Crop,
        onSuccess = { state ->
            val loaded = state.result.dataSource
            if (loaded == DataSource.DISK || loaded == DataSource.NETWORK) {
                scope.launch { alpha.animateTo(1f, fade) }
            } else {
                scope.launch(start = CoroutineStart.UNDISPATCHED) { alpha.snapTo(1f) }
            }
        },
    )
}

/**
 * What an item shows until its image is loaded, and instead of it while it is a hidden spoiler: the [minithumbnail]
 * blurred, cropped to fill [displaySize]. The blur needs Android 12; before that, and without a minithumbnail, it is a
 * tonal placeholder in `surfaceContainerHigh`. The minithumbnail is decoded once per item (it is about 40 px wide).
 */
@Composable
internal fun BlurredMinithumbnail(
    minithumbnail: Minithumbnail?,
    displaySize: DpSize,
    modifier: Modifier = Modifier,
) {
    val bitmap =
        if (minithumbnail != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            remember(minithumbnail) { decodeMinithumbnail(minithumbnail) }
        } else {
            null
        }
    if (bitmap == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.blur(minithumbnailBlur(bitmap, displaySize), BlurredEdgeTreatment.Rectangle),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun decodeMinithumbnail(minithumbnail: Minithumbnail): ImageBitmap? =
    BitmapFactory.decodeByteArray(minithumbnail.jpeg, 0, minithumbnail.jpeg.size)?.asImageBitmap()

/** A blur of [MINITHUMBNAIL_BLUR_PIXELS] pixels of [bitmap] at the scale that stretches it to [displaySize]. */
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
