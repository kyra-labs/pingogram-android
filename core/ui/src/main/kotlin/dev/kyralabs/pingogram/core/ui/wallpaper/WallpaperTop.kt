package dev.kyralabs.pingogram.core.ui.wallpaper

import android.graphics.Bitmap
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.SuccessResult
import coil3.toBitmap
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.theme.isDarkTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The mean relative luminance (0 black, 1 white) of the top of [wallpaper] as a conversation's top bar shows it,
 * blurred and dimmed by [darkThemeDimming] in dark theme: for the bar's colors and the status bar's icons
 * (`wallpaperBarColors`). A photo's is measured once, on [dispatcher], from the blurred copy Coil already keeps for the
 * bar; a fill's and a pattern's come from their colors. Null for no wallpaper, and until a photo has loaded.
 */
@Composable
fun rememberWallpaperTopLuminance(
    wallpaper: Wallpaper?,
    darkThemeDimming: Int,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): Float? {
    val context = LocalPlatformContext.current
    val window = LocalWindowInfo.current.containerSize
    val colors = MaterialTheme.colorScheme
    val dim = if (isDarkTheme()) darkThemeDimming.coerceIn(0, PERCENT) / PERCENT_F else 0f
    val luminance by produceState<Float?>(null, wallpaper, window, colors, dim) {
        value = luminanceOf(wallpaper, colors, context, window, dispatcher)?.let { it * (1f - dim) }
    }
    return luminance
}

/**
 * [rememberWallpaperTopLuminance] for the photo at [path] on this device, picked and not applied yet: the wallpaper
 * page's preview. Measured once from its blurred copy, as for a photo on Telegram.
 */
@Composable
fun rememberPhotoTopLuminance(
    path: String,
    darkThemeDimming: Int,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): Float? {
    val context = LocalPlatformContext.current
    val window = LocalWindowInfo.current.containerSize
    val dim = if (isDarkTheme()) darkThemeDimming.coerceIn(0, PERCENT) / PERCENT_F else 0f
    val luminance by produceState<Float?>(null, path, window, dim) {
        value = blurredTopLuminance(context, File(path), window, dispatcher)?.let { it * (1f - dim) }
    }
    return luminance
}

/**
 * The status bar's icons dark ([darkIcons] true) or light while this is in the composition, as they were afterwards;
 * nothing for null, and outside an activity (previews). For a conversation whose top bar shows its wallpaper.
 */
@Composable
fun StatusBarIcons(darkIcons: Boolean?) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view, darkIcons) {
        val controller = WindowCompat.getInsetsController(window, view)
        val before = controller.isAppearanceLightStatusBars
        val change = darkIcons != null && darkIcons != before
        if (change) controller.isAppearanceLightStatusBars = darkIcons == true
        onDispose { if (change) controller.isAppearanceLightStatusBars = before }
    }
}

/** [wallpaper]'s top luminance before dimming: see [rememberWallpaperTopLuminance]. */
private suspend fun luminanceOf(
    wallpaper: Wallpaper?,
    colors: ColorScheme,
    context: PlatformContext,
    window: IntSize,
    dispatcher: CoroutineDispatcher,
): Float? =
    when (wallpaper) {
        null -> {
            null
        }

        is Wallpaper.Fill -> {
            wallpaper.colors
                .map { Color(it or OPAQUE).luminance() }
                .average()
                .toFloat()
        }

        is Wallpaper.Pattern -> {
            (if (wallpaper.isInverted) colors.surfaceContainerLowest else colors.primaryContainer).luminance()
        }

        is Wallpaper.Photo -> {
            blurredTopLuminance(context, TdlibFileImage(wallpaper.image.file.id), window, dispatcher)
        }
    }

/** The top luminance of the photo at [data] as its blurred copy shows it, loaded through Coil's caches. */
private suspend fun blurredTopLuminance(
    context: PlatformContext,
    data: Any,
    window: IntSize,
    dispatcher: CoroutineDispatcher,
): Float? {
    val request = wallpaperRequest(context, BLURRED_PHOTO, data, window)
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
    return result?.image?.toBitmap()?.let { withContext(dispatcher) { it.topLuminance() } }
}

/** The mean relative luminance of this bitmap's top [TOP_FRACTION], where a top bar shows it. */
private fun Bitmap.topLuminance(): Float {
    val rows = (height * TOP_FRACTION).toInt().coerceIn(1, height)
    val pixels = IntArray(width * rows)
    getPixels(pixels, 0, width, 0, 0, width, rows)
    return pixels.map { Color(it).luminance() }.average().toFloat()
}

/** Any photo wallpaper, blurred: only its kind and blur matter to [wallpaperRequest]. */
private val BLURRED_PHOTO = Wallpaper.Photo(0, WallpaperImage(FileRef(0, 0)), isBlurred = true)

/** The share of a wallpaper's height a conversation's top bar covers, about, with the status bar. */
private const val TOP_FRACTION = 0.15f
private const val OPAQUE = 0xFF shl 24
private const val PERCENT = 100
private const val PERCENT_F = 100f
