package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

/**
 * The Daily digest's settings (`REQUIREMENTS.md` FR-X23, Settings → Notifications): whether the morning notification
 * comes ([DailyDigestSettings.enabled], off by default) and around what time ([DailyDigestSettings.time], 8:30 by
 * default), kept on this device and in backups.
 */
interface DailyDigestSettingsRepository {
    /** The settings, then every change. Never fails: unreadable settings read as the defaults. */
    val settings: Flow<DailyDigestSettings>

    /** Saves whether the digest comes; fails with `DomainError.Unknown` if it cannot be written. */
    suspend fun setEnabled(enabled: Boolean): Outcome<Unit>

    /** Saves the time it comes around, to the minute; fails with `DomainError.Unknown` if it cannot be written. */
    suspend fun setTime(time: LocalTime): Outcome<Unit>
}

/** Whether the Daily digest comes ([enabled]) and around what time of the day ([time], to the minute). */
data class DailyDigestSettings(
    val enabled: Boolean = false,
    val time: LocalTime = DEFAULT_TIME,
) {
    companion object {
        /** 8:30 in the morning. */
        val DEFAULT_TIME: LocalTime = LocalTime.of(8, 30)
    }
}
