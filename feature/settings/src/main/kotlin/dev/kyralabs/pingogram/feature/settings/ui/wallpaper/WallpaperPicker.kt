package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.domain.repository.PickedPhotoLimits
import dev.kyralabs.pingogram.core.domain.repository.PickedPhotoRepository
import dev.kyralabs.pingogram.core.domain.repository.WallpaperRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the wallpaper picker shows (owner decision 2026-11-20): the theme it picks for, the wallpapers installed on the
 * account for it, and the one picked but not applied yet, which the preview shows.
 *
 * @property forDarkTheme the theme the picker sets the wallpaper for; null until the page says which it opens on.
 * @property installed the account's installed photos and patterns for that theme.
 * @property pending the wallpaper picked and not applied yet; null shows what is set.
 * @property isWorking a photo is being prepared or a wallpaper set, so nothing else may be applied meanwhile.
 */
@Immutable
data class WallpaperPickerState(
    val forDarkTheme: Boolean? = null,
    val installed: InstalledWallpapers = InstalledWallpapers.Loading,
    val pending: WallpaperChoice? = null,
    val isWorking: Boolean = false,
)

/** The account's installed wallpapers: loading, not loaded (offline), or the photos and patterns among them. */
@Immutable
sealed interface InstalledWallpapers {
    data object Loading : InstalledWallpapers

    data object Failed : InstalledWallpapers

    data class Loaded(
        val wallpapers: ImmutableList<Wallpaper>,
    ) : InstalledWallpapers
}

/** What the user does with the account's wallpapers in the "Wallpaper" group. */
sealed interface WallpaperEvent {
    /** Picks for light or dark theme ([dark]); the page opens on the theme it shows in. */
    data class ShowTheme(
        val dark: Boolean,
    ) : WallpaperEvent

    /** Tries the installed wallpapers again. */
    data object Retry : WallpaperEvent

    /** Previews one of the installed wallpapers. */
    data class Pick(
        val wallpaper: Wallpaper,
    ) : WallpaperEvent

    /** Previews the photo at [uri], from Android's photo picker, for a screen whose longer side is [screenSidePx]. */
    data class PickPhoto(
        val uri: String,
        val screenSidePx: Int,
    ) : WallpaperEvent

    /** Blurs the photo picked, or not. */
    data class SetBlurred(
        val blurred: Boolean,
    ) : WallpaperEvent

    /** Drops the pick. */
    data object Cancel : WallpaperEvent

    /** Applies the pick for the theme picked for, a pattern on [patternFill] (the theme's colors) for other apps. */
    data class Apply(
        val patternFill: List<Int>,
    ) : WallpaperEvent

    /** Removes the account's wallpaper for the theme picked for: the theme-color one shows again. */
    data object Remove : WallpaperEvent
}

/**
 * Picking a wallpaper before applying it, for the Chat appearance page and a chat's own wallpaper: one of the account's
 * installed ones, or a photo from Android's photo picker, prepared as a JPEG copy ([PickedPhotoRepository]), blurred
 * or not; applying it hands it to the page's own way of setting it. Nothing changes until it is applied, and a failure
 * (an upload that did not finish, offline) keeps both the wallpaper there was and the pick, with [onError].
 *
 * A prepared photo's copy is deleted once applied, replaced or cancelled, and with [release].
 */
internal class WallpaperPicker(
    private val scope: CoroutineScope,
    private val wallpapers: WallpaperRepository,
    private val photos: PickedPhotoRepository,
    private val onError: (DomainError) -> Unit,
) {
    private val mutableState = MutableStateFlow(WallpaperPickerState())
    val state: StateFlow<WallpaperPickerState> = mutableState.asStateFlow()

    private var loading: Job? = null

    /** Picks for [dark] theme: what was picked for the other is dropped, and that theme's installed ones load. */
    fun showTheme(dark: Boolean) {
        if (state.value.forDarkTheme == dark) return
        replacePending(null)
        mutableState.update { it.copy(forDarkTheme = dark, installed = InstalledWallpapers.Loading) }
        loadInstalled(dark)
    }

    /** Tries the installed wallpapers again after they failed to load. */
    fun retry() {
        val dark = state.value.forDarkTheme ?: return
        mutableState.update { it.copy(installed = InstalledWallpapers.Loading) }
        loadInstalled(dark)
    }

    /** Previews one of the installed [wallpaper]s, a photo as blurred as it was set. */
    fun pick(wallpaper: Wallpaper) {
        replacePending(
            WallpaperChoice.Existing(
                wallpaper,
                isBlurred =
                    (wallpaper as? Wallpaper.Photo)?.isBlurred == true,
            ),
        )
    }

    /**
     * Prepares the photo at [uri] (from Android's photo picker) at the screen's size ([screenSidePx] on the longer
     * side, up to `PickedPhotoLimits.WALLPAPER_MAX_SIDE_PX`) and previews it; a photo that cannot be read fails.
     */
    fun pickPhoto(
        uri: String,
        screenSidePx: Int,
    ) {
        if (state.value.isWorking) return
        mutableState.update { it.copy(isWorking = true) }
        scope.launch {
            when (
                val prepared =
                    photos.preparePhoto(
                        uri,
                        screenSidePx.coerceIn(1, PickedPhotoLimits.WALLPAPER_MAX_SIDE_PX),
                    )
            ) {
                is Outcome.Success -> replacePending(WallpaperChoice.NewPhoto(prepared.value))
                is Outcome.Failure -> onError(prepared.error)
            }
            mutableState.update { it.copy(isWorking = false) }
        }
    }

    /** Blurs the photo picked, or not; patterns have no blur. */
    fun setBlurred(blurred: Boolean) {
        mutableState.update { state ->
            val pending =
                when (val choice = state.pending) {
                    is WallpaperChoice.NewPhoto -> {
                        choice.copy(isBlurred = blurred)
                    }

                    is WallpaperChoice.Existing -> {
                        if (choice.wallpaper is Wallpaper.Photo) choice.copy(isBlurred = blurred) else choice
                    }

                    null -> {
                        null
                    }
                }
            state.copy(pending = pending)
        }
    }

    /** Drops the pick: the preview shows what is set again. */
    fun cancel() = replacePending(null)

    /**
     * Applies the pick with [set], a pattern on [patternFill] (the theme's colors, 24-bit RGB) for the other apps.
     * Done, the pick is cleared; failed, it stays for another try and [onError] says why.
     */
    fun apply(
        patternFill: List<Int>,
        set: suspend (WallpaperChoice, forDarkTheme: Boolean) -> Outcome<Unit>,
    ) {
        val current = state.value
        val choice = current.pending ?: return
        if (current.isWorking) return
        val withFill = (choice as? WallpaperChoice.Existing)?.copy(patternFill = patternFill) ?: choice
        mutableState.update { it.copy(isWorking = true) }
        scope.launch {
            when (val applied = set(withFill, current.forDarkTheme == true)) {
                is Outcome.Success -> if (state.value.pending == choice) replacePending(null)
                is Outcome.Failure -> onError(applied.error)
            }
            mutableState.update { it.copy(isWorking = false) }
        }
    }

    /** Deletes a prepared photo's copy; for the view model's end. */
    fun release() = replacePending(null)

    private fun replacePending(choice: WallpaperChoice?) {
        val previous = state.value.pending
        mutableState.update { it.copy(pending = choice) }
        if (previous is WallpaperChoice.NewPhoto && previous != choice) photos.discardPhoto(previous.path)
    }

    private fun loadInstalled(dark: Boolean) {
        loading?.cancel()
        loading =
            scope.launch {
                val installed =
                    when (val answer = wallpapers.installed(dark)) {
                        is Outcome.Success -> {
                            InstalledWallpapers.Loaded(answer.value.filter { it !is Wallpaper.Fill }.toImmutableList())
                        }

                        is Outcome.Failure -> {
                            InstalledWallpapers.Failed
                        }
                    }
                mutableState.update { it.copy(installed = installed) }
            }
    }
}
