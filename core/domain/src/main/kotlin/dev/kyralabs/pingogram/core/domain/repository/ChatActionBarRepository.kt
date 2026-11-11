package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome

/**
 * What a chat's action bar does beyond blocking, adding a contact and reporting, which their own repositories do
 * (td_api.tl `removeChatActionBar`, `sharePhoneNumber`). TDLib then reports the bar gone with `updateChatActionBar`.
 */
interface ChatActionBarRepository {
    /** Dismisses chat [chatId]'s action bar, for good. */
    suspend fun dismiss(chatId: Long): Outcome<Unit>

    /** Shares the user's phone number with user [userId], a contact. */
    suspend fun sharePhoneNumber(userId: Long): Outcome<Unit>
}
