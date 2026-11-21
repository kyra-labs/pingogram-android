package dev.kyralabs.pingogram.core.ui.wallpaper

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.theme.wallpaperSeed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads a downscaled copy of a photo wallpaper's pixels (ARGB) for its seed; null if it cannot be loaded. */
fun interface WallpaperSeedPixels {
    suspend fun load(image: WallpaperImage): IntArray?
}

/**
 * [WallpaperSeedPixels] through the app's Coil image loader: Telegram's thumbnail of the background where it has one,
 * else the photo itself, at most [SEED_SIZE_PX] on a side, about the area Android analyses the system wallpaper at.
 */
class CoilWallpaperSeedPixels(
    private val context: Context,
) : WallpaperSeedPixels {
    override suspend fun load(image: WallpaperImage): IntArray? {
        val request =
            ImageRequest
                .Builder(context)
                .data(TdlibFileImage((image.thumbnail ?: image.file).id))
                .size(SEED_SIZE_PX)
                .allowHardware(false)
                .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
        return result?.image?.toBitmap()?.argbPixels()
    }
}

private fun Bitmap.argbPixels(): IntArray =
    IntArray(width * height).also {
        getPixels(it, 0, width, 0, 0, width, height)
    }

/**
 * Finds a photo [wallpaper]'s seed color when it is not known yet ([seedKnown] false): its pixels from [pixels], then
 * Material's pick on [dispatcher], given to [onSeed] to keep (`WallpaperSeedRepository`). For the first time a chat
 * whose wallpaper someone else set opens: until then the conversation shows the app's colors. Does nothing for a
 * pattern, a fill or no wallpaper, nor when the photo cannot be loaded (offline: tried again next time).
 */
@Composable
fun PhotoSeedEffect(
    wallpaper: Wallpaper?,
    seedKnown: Boolean,
    onSeed: (wallpaperId: Long, seed: Int) -> Unit,
    pixels: WallpaperSeedPixels = rememberCoilWallpaperSeedPixels(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    val photo = wallpaper as? Wallpaper.Photo
    val currentOnSeed by rememberUpdatedState(onSeed)
    LaunchedEffect(photo?.id, seedKnown) {
        val loaded = if (photo != null && !seedKnown) pixels.load(photo.image) else null
        if (photo != null && loaded != null) currentOnSeed(photo.id, withContext(dispatcher) { wallpaperSeed(loaded) })
    }
}

@Composable
private fun rememberCoilWallpaperSeedPixels(): WallpaperSeedPixels {
    val context = LocalContext.current
    return remember(context) { CoilWallpaperSeedPixels(context.applicationContext) }
}

/** The longer side of the copy a seed is found in: 112 × 112 pixels is what Android analyses wallpapers at. */
private const val SEED_SIZE_PX = 112
