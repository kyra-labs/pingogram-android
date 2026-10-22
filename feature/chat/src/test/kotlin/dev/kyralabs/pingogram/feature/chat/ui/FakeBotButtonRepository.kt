package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.CallbackAnswer
import dev.kyralabs.pingogram.core.domain.model.CallbackPayload
import dev.kyralabs.pingogram.core.domain.model.LoginUrlInfo
import dev.kyralabs.pingogram.core.domain.repository.BotButtonRepository
import kotlinx.collections.immutable.ImmutableList

/**
 * Bots' buttons: answers with what tests set (no answer, a link to open, a login link) and records every callback and
 * login request.
 */
class FakeBotButtonRepository : BotButtonRepository {
    var onAnswer: suspend (CallbackPayload) -> Outcome<CallbackAnswer> = { Outcome.Success(CallbackAnswer.NoAnswer) }
    var onLoginUrlInfo: suspend (Long) -> Outcome<LoginUrlInfo> = {
        Outcome.Success(LoginUrlInfo.Open("https://example.org", skipConfirmation = false))
    }
    var onLoginUrl: suspend (Boolean) -> Outcome<String> = { Outcome.Success("https://example.org/?auth=1") }

    /** The message and payload of every [answer] call. */
    val answered = mutableListOf<Pair<Long, CallbackPayload>>()

    /** The button and whether the bot may write, of every [loginUrl] call. */
    val loggedIn = mutableListOf<Pair<Long, Boolean>>()

    override suspend fun answer(
        chatId: Long,
        messageId: Long,
        payload: CallbackPayload,
    ): Outcome<CallbackAnswer> {
        answered += messageId to payload
        return onAnswer(payload)
    }

    override suspend fun loginUrlInfo(
        chatId: Long,
        messageId: Long,
        buttonId: Long,
    ): Outcome<LoginUrlInfo> = onLoginUrlInfo(buttonId)

    override suspend fun loginUrl(
        chatId: Long,
        messageId: Long,
        buttonId: Long,
        allowWriteAccess: Boolean,
    ): Outcome<String> {
        loggedIn += buttonId to allowWriteAccess
        return onLoginUrl(allowWriteAccess)
    }

    override suspend fun shareUsers(
        chatId: Long,
        messageId: Long,
        buttonId: Int,
        userIds: ImmutableList<Long>,
        onlyCheck: Boolean,
    ): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun shareChat(
        chatId: Long,
        messageId: Long,
        buttonId: Int,
        sharedChatId: Long,
        onlyCheck: Boolean,
    ): Outcome<Unit> = Outcome.Success(Unit)
}
