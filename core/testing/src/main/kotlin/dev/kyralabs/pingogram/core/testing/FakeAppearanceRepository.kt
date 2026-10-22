package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.BubbleCorners
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [AppearanceRepository] in memory, for tests of what follows the Appearance settings. [appearance] emits nothing until
 * [save] or a setter, as while the settings file is being read; [initial] saves those settings at once.
 */
class FakeAppearanceRepository(
    initial: AppearanceSettings? = null,
) : AppearanceRepository {
    private val saved = MutableSharedFlow<AppearanceSettings>(replay = 1)
    private var current = initial ?: AppearanceSettings()

    /** What the setters fail with; null saves. */
    var failure: DomainError? = null

    override val appearance: Flow<AppearanceSettings> = saved.distinctUntilChanged()

    init {
        initial?.let(::save)
    }

    /** Saves [settings], as if read from the settings file. */
    fun save(settings: AppearanceSettings) {
        current = settings
        saved.tryEmit(settings)
    }

    override suspend fun setThemeMode(themeMode: ThemeMode) = change { it.copy(themeMode = themeMode) }

    override suspend fun setDynamicColor(enabled: Boolean) = change { it.copy(dynamicColor = enabled) }

    override suspend fun setPalette(palette: ThemePalette) = change { it.copy(palette = palette, dynamicColor = false) }

    override suspend fun setContrast(contrast: Contrast) = change { it.copy(contrast = contrast) }

    override suspend fun setAmoled(enabled: Boolean) = change { it.copy(amoled = enabled) }

    override suspend fun setChatTextScale(scale: Float) =
        change { it.copy(chat = it.chat.copy(textScale = ChatAppearance.textScaleOf(scale))) }

    override suspend fun setBubbleCorners(corners: BubbleCorners) =
        change { it.copy(chat = it.chat.copy(bubbleCorners = corners)) }

    override suspend fun setChatWallpaper(wallpaper: ChatWallpaper) =
        change { it.copy(chat = it.chat.copy(wallpaper = wallpaper)) }

    private fun change(transform: (AppearanceSettings) -> AppearanceSettings): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        save(transform(current))
        return Outcome.Success(Unit)
    }
}
