package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * The chats whose translate bar the user closed (message translation, `REQUIREMENTS.md` FR-C3), kept on this device
 * across logouts, like the pinned messages bars they hid ([HiddenPinsRepository]). A closed bar stays closed for that
 * chat; its single messages still translate from their actions.
 */
interface HiddenTranslateBarsRepository {
    /**
     * Whether the translate bar of chat [chatId] was closed, then every change. Never fails: what cannot be read counts
     * as shown.
     */
    fun isHidden(chatId: Long): Flow<Boolean>

    /** Closes the translate bar of chat [chatId]; fails with `DomainError.Unknown` if it cannot be written. */
    suspend fun hide(chatId: Long): Outcome<Unit>
}
