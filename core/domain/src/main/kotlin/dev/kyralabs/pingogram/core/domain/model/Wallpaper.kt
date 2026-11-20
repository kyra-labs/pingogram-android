package dev.kyralabs.pingogram.core.domain.model

/**
 * A wallpaper the account keeps on Telegram (td_api.tl `background`; owner decision 2026-11-20): one of Telegram's,
 * a photo the user uploaded, or a fill set by another app. It shows in the user's other apps too. The theme-color
 * wallpapers ([ChatWallpaper]) stay on this device.
 */
sealed interface Wallpaper {
    /** TDLib's id of the background, which `setDefaultBackground` takes back for one already on Telegram. */
    val id: Long

    /**
     * A photo (`backgroundTypeWallpaper`), a JPEG in [image]; [isBlurred] asks every app to draw it downscaled to fit
     * a 450 × 450 square and box-blurred with radius 12.
     */
    data class Photo(
        override val id: Long,
        val image: WallpaperImage,
        val isBlurred: Boolean,
    ) : Wallpaper

    /**
     * A pattern (`backgroundTypePattern`) in [image], drawn over a fill; Pingogram fills it with the theme's colors,
     * not the ones stored with it, so it follows the color theme. [intensity] (0–100) is how strongly it shows over
     * the fill; [isInverted] (dark themes) fills the pattern itself and leaves the rest dark.
     */
    data class Pattern(
        override val id: Long,
        val image: WallpaperImage,
        val intensity: Int,
        val isInverted: Boolean,
    ) : Wallpaper

    /**
     * A color or gradient (`backgroundTypeFill`) another app set, as 24-bit RGB [colors]: one for a solid color, two
     * from top to bottom turned by [rotationDegrees], or three or four for a freeform gradient. Pingogram does not
     * offer fills; it shows one the account or a chat has.
     */
    data class Fill(
        override val id: Long,
        val colors: List<Int>,
        val rotationDegrees: Int = 0,
    ) : Wallpaper
}

/**
 * The file of a photo or pattern [Wallpaper] and a small preview of it.
 *
 * @property file the full image: a JPEG for a photo; a PNG, or a gzipped vector pattern when [isVector], for a pattern.
 * @property isVector the pattern is a vector one (MIME type `application/x-tgwallpattern`, a subset of SVG).
 * @property thumbnail TDLib's thumbnail, a JPEG for a photo and a PNG for a pattern; null when it has none.
 */
data class WallpaperImage(
    val file: FileRef,
    val isVector: Boolean = false,
    val thumbnail: FileRef? = null,
)

/**
 * A chat's own wallpaper (td_api.tl `chatBackground`), which either person in a private chat may set; it shows in that
 * chat instead of the account's.
 *
 * @property darkThemeDimming how much it is dimmed in dark theme, 0–100 percent.
 */
data class ChatWallpaperOverride(
    val wallpaper: Wallpaper,
    val darkThemeDimming: Int,
)

/** The account's wallpapers for light and dark theme (`setDefaultBackground`'s `for_dark_theme`); null for none. */
data class AccountWallpapers(
    val light: Wallpaper? = null,
    val dark: Wallpaper? = null,
) {
    /** The one for [dark] theme. */
    fun forTheme(dark: Boolean): Wallpaper? = if (dark) this.dark else light
}

/** A wallpaper to set: one already on Telegram ([Existing]) or a photo from this device ([NewPhoto]). */
sealed interface WallpaperChoice {
    /**
     * [wallpaper], blurred with [isBlurred] if it is a photo; a pattern takes [patternFill] (24-bit RGB, one to four
     * colors) as the fill other apps draw it on.
     */
    data class Existing(
        val wallpaper: Wallpaper,
        val isBlurred: Boolean = false,
        val patternFill: List<Int> = emptyList(),
    ) : WallpaperChoice

    /** The JPEG at [path] (a copy `PickedPhotoRepository` prepared), uploaded to Telegram, blurred with [isBlurred]. */
    data class NewPhoto(
        val path: String,
        val isBlurred: Boolean = false,
    ) : WallpaperChoice
}
