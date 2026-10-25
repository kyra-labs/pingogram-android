package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether search suggests the people the user writes to most (`docs/audit/feature-gaps-octogram.md` P3), which
 * Telegram works out for the account. Turning it off clears what Telegram counted.
 */
interface FrequentContactsRepository {
    /** Whether frequent contacts are suggested, then every change; true (Telegram's default) where unreadable. */
    val suggests: Flow<Boolean>

    /** Suggests frequent contacts ([suggests]) or stops, clearing them. */
    suspend fun setSuggests(suggests: Boolean): Outcome<Unit>
}
