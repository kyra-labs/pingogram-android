package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import dev.kyralabs.pingogram.core.ui.theme.wallpaperSeed
import dev.kyralabs.pingogram.core.ui.wallpaper.CoilWallpaperSeedPixels
import dev.kyralabs.pingogram.core.ui.wallpaper.PhotoSeedEffect
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds the seed of the photo the wallpaper page previews while it is not known ([WallpaperPageUiState.seed] null),
 * once per photo, off the main thread: one of Telegram's through [PhotoSeedEffect], one picked on the phone from its
 * prepared copy. [onSeed] gets it, for the preview's colors and for Apply to keep.
 */
@Composable
internal fun PreviewSeedEffect(
    state: WallpaperPageUiState,
    onSeed: (SeedPhoto, Int) -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    val seedKnown = state.seed != null
    val currentOnSeed by rememberUpdatedState(onSeed)
    PhotoSeedEffect(
        wallpaper = state.previewedWallpaper,
        seedKnown = seedKnown,
        onSeed = { id, seed -> currentOnSeed(SeedPhoto.OnTelegram(id), seed) },
        dispatcher = dispatcher,
    )
    val picked = state.seedPhoto as? SeedPhoto.Picked
    val context = LocalContext.current
    val pixels = remember(context) { CoilWallpaperSeedPixels(context.applicationContext) }
    LaunchedEffect(picked, seedKnown) {
        val loaded = if (picked != null && !seedKnown) pixels.load(picked.path) else null
        if (picked != null && loaded != null) currentOnSeed(picked, withContext(dispatcher) { wallpaperSeed(loaded) })
    }
}
