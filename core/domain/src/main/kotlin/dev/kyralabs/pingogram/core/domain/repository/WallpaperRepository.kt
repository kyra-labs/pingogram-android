package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AccountWallpapers
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import kotlinx.coroutines.flow.StateFlow

/**
 * The wallpapers the account keeps on Telegram (`docs/TDLIB.md` "Backgrounds"; owner decision 2026-11-20): one for
 * light and one for dark theme, and the ones the account installed. Setting one changes it in the user's other apps
 * too. A failed change (an upload that did not finish, a refusal) leaves the previous wallpaper in place.
 */
interface WallpaperRepository {
    /** The account's wallpapers, as TDLib announces them; none until it has. */
    val defaults: StateFlow<AccountWallpapers>

    /**
     * The wallpapers installed on the account, suggested for [forDarkTheme], in TDLib's order, without chat themes.
     */
    suspend fun installed(forDarkTheme: Boolean): Outcome<List<Wallpaper>>

    /** Makes [choice] the account's wallpaper for [forDarkTheme], uploading a new photo first. */
    suspend fun setDefault(
        choice: WallpaperChoice,
        forDarkTheme: Boolean,
    ): Outcome<Unit>

    /** Removes the account's wallpaper for [forDarkTheme]; the theme-color one shows again. */
    suspend fun removeDefault(forDarkTheme: Boolean): Outcome<Unit>
}
