package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AppearanceRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). Enums are stored by
 * name, so reordering them keeps what was saved; a name that no longer exists, a file that cannot be read, and a
 * missing key all read as the default.
 */
@Singleton
class DataStoreAppearanceRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : AppearanceRepository {
        override val appearance: Flow<AppearanceSettings> =
            dataStore.data
                // Unreadable settings must not leave the app without a theme.
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { it.toAppearanceSettings() }
                .distinctUntilChanged()

        override suspend fun setThemeMode(themeMode: ThemeMode) = edit { it[THEME_MODE] = themeMode.name }

        override suspend fun setDynamicColor(enabled: Boolean) = edit { it[DYNAMIC_COLOR] = enabled }

        override suspend fun setPalette(palette: ThemePalette) =
            edit {
                it[PALETTE] = palette.name
                it[DYNAMIC_COLOR] = false
            }

        override suspend fun setContrast(contrast: Contrast) = edit { it[CONTRAST] = contrast.name }

        override suspend fun setAmoled(enabled: Boolean) = edit { it[AMOLED] = enabled }

        override suspend fun setChatTextScale(scale: Float) =
            edit { it[CHAT_TEXT_SCALE] = ChatAppearance.textScaleOf(scale) }

        override suspend fun setBubbleCorners(corners: BubbleCorners) = edit { it[BUBBLE_CORNERS] = corners.name }

        override suspend fun setChatWallpaper(wallpaper: ChatWallpaper) = edit { it[CHAT_WALLPAPER] = wallpaper.name }

        private suspend fun edit(change: (MutablePreferences) -> Unit): Outcome<Unit> =
            try {
                dataStore.edit(change)
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

private fun Preferences.toAppearanceSettings(): AppearanceSettings {
    val defaults = AppearanceSettings()
    return AppearanceSettings(
        themeMode = enumOrNull<ThemeMode>(this[THEME_MODE]) ?: defaults.themeMode,
        dynamicColor = this[DYNAMIC_COLOR] ?: defaults.dynamicColor,
        palette = enumOrNull<ThemePalette>(this[PALETTE]) ?: defaults.palette,
        contrast = enumOrNull<Contrast>(this[CONTRAST]) ?: defaults.contrast,
        amoled = this[AMOLED] ?: defaults.amoled,
        chat =
            ChatAppearance(
                // A value from another version's range or steps reads as the nearest step of this one.
                textScale = this[CHAT_TEXT_SCALE]?.let(ChatAppearance::textScaleOf) ?: defaults.chat.textScale,
                bubbleCorners = enumOrNull<BubbleCorners>(this[BUBBLE_CORNERS]) ?: defaults.chat.bubbleCorners,
                wallpaper = enumOrNull<ChatWallpaper>(this[CHAT_WALLPAPER]) ?: defaults.chat.wallpaper,
            ),
    )
}

private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? = enumValues<E>().firstOrNull { it.name == name }

// Keys of the settings file; renaming one loses what users saved under it.
private val THEME_MODE = stringPreferencesKey("appearance.theme_mode")
private val DYNAMIC_COLOR = booleanPreferencesKey("appearance.dynamic_color")
private val PALETTE = stringPreferencesKey("appearance.palette")
private val CONTRAST = stringPreferencesKey("appearance.contrast")
private val AMOLED = booleanPreferencesKey("appearance.amoled")
private val CHAT_TEXT_SCALE = floatPreferencesKey("appearance.chat.text_scale")
private val BUBBLE_CORNERS = stringPreferencesKey("appearance.chat.bubble_corners")
private val CHAT_WALLPAPER = stringPreferencesKey("appearance.chat.wallpaper")
