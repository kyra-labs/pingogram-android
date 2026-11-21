package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.repository.WallpaperSeedRepository
import dev.kyralabs.pingogram.core.ui.theme.fillSeed
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/**
 * The seed of the wallpaper the page previews (`docs/adr/0041-chat-colors-from-the-wallpaper.md`): a photo's kept one
 * ([WallpaperSeedRepository]) or the one found for it while the page is open ([found]), a fill's from its colors;
 * none for a pattern, drawn in the theme's colors. Apply keeps the seed of a photo it set ([keep]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class WallpaperPageSeeds(
    private val repository: WallpaperSeedRepository,
) {
    private val foundSeeds = MutableStateFlow(persistentMapOf<SeedPhoto, Int>())

    /**
     * Each of [states] with the seed of what it previews ([WallpaperPageUiState.seed]); a seed found for an earlier
     * preview never shows with a later one.
     */
    fun on(states: Flow<WallpaperPageUiState>): Flow<WallpaperPageUiState> {
        val keyed: Flow<Pair<SeedKey, Int?>?> =
            states
                .map { it.seedKey }
                .distinctUntilChanged()
                .flatMapLatest { key -> seedOf(key).map { key to it } }
        return combine(states, keyed.onStart { emit(null) }) { state, seed ->
            state.copy(seed = seed?.takeIf { it.first == state.seedKey }?.second)
        }
    }

    private fun seedOf(key: SeedKey): Flow<Int?> {
        val (fill, photo) = key
        return when {
            fill != null -> {
                flowOf(fillSeed(fill.colors.map { it or OPAQUE }))
            }

            photo is SeedPhoto.OnTelegram -> {
                combine(repository.seed(photo.wallpaperId), foundSeeds) { kept, found -> kept ?: found[photo] }
            }

            photo != null -> {
                foundSeeds.map { it[photo] }
            }

            else -> {
                flowOf(null)
            }
        }
    }

    /** The [seed] found for [photo], from its pixels; for this page only until Apply keeps it. */
    fun found(
        photo: SeedPhoto,
        seed: Int,
    ) = foundSeeds.update { it.putting(photo, seed) }

    /**
     * Keeps [seed] for background [wallpaperId], the photo Apply just set; nothing without one (not found yet: the
     * conversation finds it). If it cannot be kept, the conversation finds it again.
     */
    suspend fun keep(
        wallpaperId: Long,
        seed: Int?,
    ) {
        if (seed != null) repository.setSeed(wallpaperId, seed)
    }
}

/** What a preview's seed depends on: the fill or the photo it shows, not how a photo is blurred. */
private typealias SeedKey = Pair<Wallpaper.Fill?, SeedPhoto?>

private val WallpaperPageUiState.seedKey: SeedKey get() = (previewedWallpaper as? Wallpaper.Fill) to seedPhoto

/** Telegram's fill colors are 24-bit RGB; a seed is opaque. */
private const val OPAQUE = 0xFF shl 24
