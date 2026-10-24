package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PowerSaving
import dev.kyralabs.pingogram.core.domain.model.PowerSavingMode
import dev.kyralabs.pingogram.core.domain.model.PowerSavingPart
import dev.kyralabs.pingogram.core.domain.model.PowerSavingSettings
import kotlinx.coroutines.flow.Flow

/**
 * The power saving settings (Settings → Power saving): when it is on and what it turns off. Kept on this device across
 * logouts, like the Appearance settings. Reduced motion stays the system's: power saving turns off only its parts.
 */
interface PowerSavingRepository {
    /** The settings, then every change. Never fails: settings that cannot be read count as the defaults. */
    val settings: Flow<PowerSavingSettings>

    /** Saves the [mode]; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setMode(mode: PowerSavingMode): Outcome<Unit>

    /** Saves whether power saving turns [part] off; fails with `DomainError.Unknown` if it cannot be written. */
    suspend fun setPart(
        part: PowerSavingPart,
        off: Boolean,
    ): Outcome<Unit>
}

/** Whether Android's Battery Saver is on. */
interface BatterySaverRepository {
    /** Whether Battery Saver is on now, then every change. */
    val isOn: Flow<Boolean>
}

/** What power saving turns off now, from its settings and Battery Saver ([PowerSavingSettings.inEffect]). */
interface PowerSavingPolicy {
    /** What is turned off now, then every change. */
    val inEffect: Flow<PowerSaving>
}
