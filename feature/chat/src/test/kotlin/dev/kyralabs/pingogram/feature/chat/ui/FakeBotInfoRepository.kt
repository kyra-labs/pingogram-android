package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatBots
import dev.kyralabs.pingogram.core.domain.repository.BotInfoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Bots: each chat's bots as tests set them ([setBots]), none by default, following changes as TDLib's updates would;
 * starts answer with [onStart] and are recorded ([started]).
 */
class FakeBotInfoRepository : BotInfoRepository {
    private val bots = MutableStateFlow<Map<Long, Outcome<ChatBots>>>(emptyMap())

    var onStart: suspend () -> Outcome<Unit> = { Outcome.Success(Unit) }

    /** The bot, chat and parameter of every [sendStartMessage] call. */
    val started = mutableListOf<Triple<Long, Long, String>>()

    fun setBots(
        chatId: Long,
        chatBots: Outcome<ChatBots>,
    ) {
        bots.value += chatId to chatBots
    }

    fun setBots(
        chatId: Long,
        chatBots: ChatBots,
    ) = setBots(chatId, Outcome.Success(chatBots))

    override fun chatBots(chatId: Long): Flow<Outcome<ChatBots>> =
        bots.map { it[chatId] ?: Outcome.Success(ChatBots.None) }.distinctUntilChanged()

    override suspend fun sendStartMessage(
        botUserId: Long,
        chatId: Long,
        parameter: String,
    ): Outcome<Unit> {
        started += Triple(botUserId, chatId, parameter)
        return onStart()
    }
}
