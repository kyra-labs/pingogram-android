package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * The Appearance settings (`REQUIREMENTS.md` FR-S2), kept on this device: the app's theme follows [appearance], and
 * the Appearance page changes it. Settings stay when the user logs out; they belong to the device, not the account.
 */
interface AppearanceRepository {
    /**
     * The settings, then every change. Never fails: settings that cannot be read count as the defaults
     * ([AppearanceSettings]'s), so the app always gets a theme.
     */
    val appearance: Flow<AppearanceSettings>

    /** Saves [themeMode]; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setThemeMode(themeMode: ThemeMode): Outcome<Unit>

    /** Saves whether dynamic color is on; fails as [setThemeMode] does. */
    suspend fun setDynamicColor(enabled: Boolean): Outcome<Unit>

    /** Saves [contrast]; fails as [setThemeMode] does. */
    suspend fun setContrast(contrast: Contrast): Outcome<Unit>

    /** Saves whether AMOLED black is on; fails as [setThemeMode] does. */
    suspend fun setAmoled(enabled: Boolean): Outcome<Unit>
}
