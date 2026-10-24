package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DoubleTapReactions
import kotlinx.coroutines.flow.Flow

/** Where a double tap on a message adds the quick reaction ([QuickReactionRepository]), kept on this device. */
interface DoubleTapSettingsRepository {
    /** The settings, then every change. Never fails: settings that cannot be read count as the defaults. */
    val doubleTap: Flow<DoubleTapReactions>

    /** Saves them; fails with `DomainError.Unknown` if they cannot be written (a full disk). */
    suspend fun setDoubleTap(doubleTap: DoubleTapReactions): Outcome<Unit>
}
