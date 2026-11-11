package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.feature.media.ui.BlurredMinithumbnail
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * An album's [picture] (a cover, a story's tile), cropped to fill its box: its minithumbnail blurred first, then the
 * file decoded by Coil at about [sizePx] (`docs/PERFORMANCE.md` §4), faded in on the motion scheme's default effects
 * spring once loaded from disk, at once from Coil's memory cache, and dimmed in dark theme as the conversation's media
 * (`docs/DESIGN_SYSTEM.md` §10). Without a picture, a tonal placeholder in `surfaceContainerHighest`. Decorative: the
 * tile around it describes it.
 */
@Composable
internal fun AlbumImage(
    picture: AlbumPicture?,
    sizePx: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        val fileId = picture?.fileIdFor(sizePx)
        val alpha = remember(fileId) { Animatable(0f) }
        if (picture?.minithumbnail != null) {
            BlurredMinithumbnail(picture.minithumbnail, Modifier.fillMaxSize(), covered = { alpha.value >= 1f })
        }
        if (fileId != null) {
            val context = LocalPlatformContext.current
            val request =
                remember(context, fileId, sizePx) {
                    ImageRequest
                        .Builder(context)
                        .data(TdlibFileImage(fileId))
                        .size(sizePx.coerceAtLeast(1))
                        .build()
                }
            val scope = rememberCoroutineScope()
            val fadeIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value },
                contentScale = ContentScale.Crop,
                colorFilter = MaterialTheme.pingogramColors.mediaColorFilter,
                onSuccess = { state ->
                    // Coil's own crossfade is a tween (DESIGN_SYSTEM.md §5), so the fade is ours.
                    val loaded = state.result.dataSource
                    if (loaded == DataSource.DISK || loaded == DataSource.NETWORK) {
                        scope.launch { alpha.animateTo(1f, fadeIn) }
                    } else {
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { alpha.snapTo(1f) }
                    }
                },
            )
        }
    }
}
