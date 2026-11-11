package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * The groups whose welcome pack (the owner's board 6: the welcome messages a new member sees) the user collapsed with
 * "Got it", kept on the device: the pack then shows as a bar the user can open again.
 */
interface WelcomePackRepository {
    /** Whether the user collapsed chat [chatId]'s welcome pack, then every change. */
    fun isCollapsed(chatId: Long): Flow<Boolean>

    /** Collapses chat [chatId]'s welcome pack for good on this device. */
    suspend fun collapse(chatId: Long): Outcome<Unit>
}
