package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Cleanup suggestions' settings (`REQUIREMENTS.md` FR-X24, Settings → Chats → Cleanup suggestions): whether chats are
 * suggested ([CleanupSettings.enabled], on by default) and after how many months unopened ([CleanupSettings.months],
 * 3 by default), kept on this device and in backups.
 */
interface CleanupSettingsRepository {
    /** The settings, then every change. Never fails: unreadable settings read as the defaults. */
    val settings: Flow<CleanupSettings>

    /** Saves whether chats are suggested; fails with `DomainError.Unknown` if it cannot be written. */
    suspend fun setEnabled(enabled: Boolean): Outcome<Unit>

    /** Saves [months], one of [CleanupSettings.MONTHS]; any other is not saved. */
    suspend fun setMonths(months: Int): Outcome<Unit>
}

/** Whether chats not opened for a while are suggested ([enabled]), and after how many months ([months]). */
data class CleanupSettings(
    val enabled: Boolean = true,
    val months: Int = DEFAULT_MONTHS,
) {
    companion object {
        /** The choices of "Not opened for", in months. */
        val MONTHS: List<Int> = listOf(1, 3, 6, 12)

        const val DEFAULT_MONTHS = 3
    }
}
