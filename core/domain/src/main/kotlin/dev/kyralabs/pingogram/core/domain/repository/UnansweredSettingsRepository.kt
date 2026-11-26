package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Unanswered's settings (`REQUIREMENTS.md` FR-X22, Settings → Chats): whether the Chats tab's header has its button
 * ([UnansweredSettings.showButton], on by default) and after how many days a private chat with no answer counts
 * ([UnansweredSettings.afterDays], 3 by default), kept on this device and in backups.
 */
interface UnansweredSettingsRepository {
    /** The settings, then every change. Never fails: unreadable settings read as the defaults. */
    val settings: Flow<UnansweredSettings>

    /** Saves whether the header has the button; fails with `DomainError.Unknown` if it cannot be written. */
    suspend fun setShowButton(show: Boolean): Outcome<Unit>

    /** Saves [days], one of [UnansweredSettings.DAYS]; any other is not saved. */
    suspend fun setAfterDays(days: Int): Outcome<Unit>
}

/**
 * Whether the Chats tab's header shows the Unanswered button ([showButton]), and after how many days without an answer
 * a private chat counts ([afterDays], one of [DAYS]).
 */
data class UnansweredSettings(
    val showButton: Boolean = true,
    val afterDays: Int = DEFAULT_DAYS,
) {
    companion object {
        /** The choices of "After", in days. */
        val DAYS: List<Int> = listOf(1, 2, 3, 7, 14)

        const val DEFAULT_DAYS = 3
    }
}
