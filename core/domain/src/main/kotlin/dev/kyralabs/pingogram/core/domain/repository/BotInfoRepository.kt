package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatBots
import kotlinx.coroutines.flow.Flow

/**
 * The bots of a conversation (`REQUIREMENTS.md` FR-C5): the commands they take, the menu button and description of a
 * private chat's bot, and starting a bot. Nothing is cached here: TDLib keeps the full info of users and groups, which
 * holds all of it, and reports its changes.
 *
 * Requests that get no answer in time fail with `DomainError.Network`; a chat TDLib does not know fails with
 * `DomainError.NotFound`.
 */
interface BotInfoRepository {
    /**
     * The bots of chat [chatId], then again after every change TDLib reports while collected (a bot's commands edited
     * with @BotFather, a bot added to a group or removed): for a private chat with a bot, its commands and what it
     * shows; for a basic group or a supergroup, the commands of its bots. [ChatBots.None] for chats with people,
     * channels and secret chats. A failed first request is the flow's first value; changes still follow it.
     */
    fun chatBots(chatId: Long): Flow<Outcome<ChatBots>>

    /**
     * Starts bot [botUserId] in chat [chatId], its private chat (td_api.tl `sendBotStartMessage`): Telegram sends
     * "/start", and [parameter] (a start link's, at most 64 characters of `A-Za-z0-9_-`) to the bot with it. Only on
     * the user's tap.
     */
    suspend fun sendStartMessage(
        botUserId: Long,
        chatId: Long,
        parameter: String = "",
    ): Outcome<Unit>
}
