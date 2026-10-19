package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.NotificationPrompt
import kotlinx.coroutines.flow.Flow

/**
 * The answers to the request to allow notifications ([NotificationPrompt]), kept on this device across logouts, like
 * the Appearance settings.
 */
interface NotificationPromptRepository {
    /** The answers, then every change. Never fails: answers that cannot be read count as none. */
    val prompt: Flow<NotificationPrompt>

    /** Saves that the user chose "Not now"; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun dismiss(): Outcome<Unit>

    /** Saves that the system's permission dialog was shown; fails as [dismiss] does. */
    suspend fun setRequested(): Outcome<Unit>
}
