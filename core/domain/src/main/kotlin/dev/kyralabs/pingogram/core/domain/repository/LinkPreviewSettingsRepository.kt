package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether messages sent to secret chats get link previews (Privacy and security), kept on this device and off by
 * default: Telegram's servers make the preview, so the link leaves the end-to-end encryption.
 */
interface LinkPreviewSettingsRepository {
    /** Whether secret chats get link previews, then every change. Never fails: unreadable settings read as off. */
    val secretChatPreviews: Flow<Boolean>

    /** Saves it; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setSecretChatPreviews(enabled: Boolean): Outcome<Unit>
}
