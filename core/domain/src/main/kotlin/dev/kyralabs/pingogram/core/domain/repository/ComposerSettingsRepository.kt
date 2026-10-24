package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import kotlinx.coroutines.flow.Flow

/**
 * How the composer takes Enter ([SendKeys]): kept on this device across logouts, like the Appearance settings, and set
 * in Appearance → Chats.
 */
interface ComposerSettingsRepository {
    /** Which Enter sends, then every change. Never fails: settings that cannot be read count as the defaults. */
    val sendKeys: Flow<SendKeys>

    /** Saves [keys]; fails with `DomainError.Unknown` if they cannot be written (a full disk). */
    suspend fun setSendKeys(keys: SendKeys): Outcome<Unit>
}
