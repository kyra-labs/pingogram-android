package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import kotlinx.coroutines.flow.Flow

/**
 * The chats whose pinned messages bar the user hid without unpinning (`REQUIREMENTS.md` FR-C9: members who may not
 * unpin), kept on this device across logouts, like the Appearance settings. A chat stays hidden up to the newest
 * message pinned when it was hidden; a message pinned later shows the bar again. The bar of each forum topic or
 * message thread ([MessageTopic]) is hidden on its own.
 */
interface HiddenPinsRepository {
    /**
     * The newest pinned message of chat [chatId] when its bar was hidden, then every change; null while the bar was
     * never hidden. Never fails: what cannot be read counts as never hidden.
     */
    fun hiddenUpTo(
        chatId: Long,
        topic: MessageTopic? = null,
    ): Flow<Long?>

    /**
     * Hides the bar of chat [chatId] up to message [messageId], its newest pinned message; fails with
     * `DomainError.Unknown` if it cannot be written (a full disk).
     */
    suspend fun hide(
        chatId: Long,
        messageId: Long,
        topic: MessageTopic? = null,
    ): Outcome<Unit>
}
