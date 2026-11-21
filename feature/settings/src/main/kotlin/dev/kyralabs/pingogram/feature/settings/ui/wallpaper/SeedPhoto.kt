package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

/** A photo the wallpaper page's preview takes its colors from: one of Telegram's, or one picked on the phone. */
sealed interface SeedPhoto {
    data class OnTelegram(
        val wallpaperId: Long,
    ) : SeedPhoto

    data class Picked(
        val path: String,
    ) : SeedPhoto
}
