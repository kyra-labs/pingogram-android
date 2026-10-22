package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.domain.model.ThumbnailFormat
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * A square image of the profile (a profile photo in the carousel, a photo, video or GIF in the shared media grid):
 * TDLib file [fileId] decoded by Coil at [sizePx] × [sizePx] (`docs/PERFORMANCE.md` §4), downloaded at visible priority
 * by the fetcher, faded in on the motion scheme's default effects spring over a tonal placeholder in
 * `surfaceContainerHighest` once it is loaded from disk, at once when Coil's memory cache has it, so a grid does not
 * flicker while it scrolls. [colorFilter] tints it, e.g. the dark theme's dim of media
 * (`pingogramColors.mediaColorFilter`, `docs/DESIGN_SYSTEM.md` §10). Without a file only the placeholder shows.
 * Decorative: the item around it describes it.
 */
@Composable
internal fun ProfileImage(
    fileId: Int?,
    sizePx: Int,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (fileId != null) {
            val alpha = remember(fileId) { Animatable(0f) }
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
                colorFilter = colorFilter,
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

/**
 * The file of the smallest size of the photo whose shorter side covers [sizePx] (so a square crop of it fills the
 * cell), or the largest one if none does (`docs/PERFORMANCE.md` §7); null for a photo without sizes.
 */
internal fun Photo.fileIdFor(sizePx: Int): Int? {
    val bySide = sizes.sortedBy { minOf(it.width, it.height) }
    return (bySide.firstOrNull { minOf(it.width, it.height) >= sizePx } ?: bySide.lastOrNull())?.file?.id
}

/** The file of a video's or GIF's preview image, if it is one Coil decodes (not an MPEG-4 or WebM preview). */
internal val Thumbnail.imageFileId: Int?
    get() =
        when (format) {
            ThumbnailFormat.Jpeg, ThumbnailFormat.Png, ThumbnailFormat.Webp, ThumbnailFormat.Gif -> file.id
            ThumbnailFormat.Mpeg4, ThumbnailFormat.Webm, ThumbnailFormat.Tgs -> null
        }
