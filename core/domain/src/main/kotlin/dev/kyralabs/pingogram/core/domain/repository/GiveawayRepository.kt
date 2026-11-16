package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.GiveawayInfo

/**
 * What TDLib knows of a giveaway for the user (`getGiveawayInfo`), asked for when the user opens its details
 * ("Learn more"); nothing is asked before and nothing is bought or entered through Pingogram.
 */
interface GiveawayRepository {
    /** The giveaway of message [messageId] of chat [chatId], or of the message that announced its winners. */
    suspend fun info(
        chatId: Long,
        messageId: Long,
    ): Outcome<GiveawayInfo>
}
