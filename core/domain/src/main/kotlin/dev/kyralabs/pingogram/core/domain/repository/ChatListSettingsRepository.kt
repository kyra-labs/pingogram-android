package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * How the chat list looks (Appearance → Chat list), kept on this device: whether the story rings show above the chats
 * ([ChatListSettings.showStories], on by default). Stories themselves stay with Telegram; hiding the rings loads none.
 */
interface ChatListSettingsRepository {
    /** The settings, then every change. Never fails: unreadable settings read as the defaults. */
    val settings: Flow<ChatListSettings>

    /** Saves whether the rings show; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setShowStories(show: Boolean): Outcome<Unit>
}

/** The chat list's own settings: whether the story rings show above the chats. */
data class ChatListSettings(
    val showStories: Boolean = true,
)
