package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import dev.kyralabs.pingogram.core.domain.repository.WallpaperRepository
import dev.kyralabs.pingogram.core.domain.repository.WallpaperSeedRepository
import javax.inject.Inject

/**
 * The wallpaper page's wallpapers on Telegram and the seeds kept of their photos
 * (`docs/adr/0041-chat-colors-from-the-wallpaper.md`): one parameter of [WallpaperPageViewModel], which already takes
 * one repository per kind of data it combines.
 */
class WallpaperRepositories
    @Inject
    constructor(
        val wallpapers: WallpaperRepository,
        val seeds: WallpaperSeedRepository,
    )
