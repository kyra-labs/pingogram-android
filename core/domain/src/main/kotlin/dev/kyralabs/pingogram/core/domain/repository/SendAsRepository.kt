package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AvailableSender
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import kotlinx.collections.immutable.ImmutableList

/**
 * Who the user sends messages as in a group or channel (send-as, `REQUIREMENTS.md` FR-C5). The current choice is the
 * chat's `Chat.messageSender`, which `ChatRepository.chat` follows. A request that gets no answer in time fails with
 * `DomainError.Network`.
 */
interface SendAsRepository {
    /**
     * Everyone the user can send messages in chat [chatId] as, asked from Telegram each time: the user first, then the
     * chats. TDLib announces those chats before it answers, so `ChatRepository.chat` knows them.
     */
    suspend fun availableSenders(chatId: Long): Outcome<ImmutableList<AvailableSender>>

    /**
     * Sends new messages in chat [chatId] as [sender], one of [availableSenders]: once TDLib accepted it,
     * `Chat.messageSender` follows. Fails for a sender that needs Telegram Premium the user does not have.
     */
    suspend fun setSender(
        chatId: Long,
        sender: MessageSender,
    ): Outcome<Unit>
}
