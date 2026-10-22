package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.CallbackAnswer
import dev.kyralabs.pingogram.core.domain.model.CallbackPayload
import dev.kyralabs.pingogram.core.domain.model.LoginUrlInfo
import kotlinx.collections.immutable.ImmutableList

/**
 * What the buttons bots attach to messages ask of Telegram (`Message.replyMarkup`): each call is one tap of the user's,
 * never made on its own (Telegram's API terms). The buttons themselves come with their message, current through
 * `updateMessageEdited`. A request that gets no answer in time fails with `DomainError.Network`, except [answer].
 */
interface BotButtonRepository {
    /**
     * Sends [payload], a callback button of message [messageId] in chat [chatId], to its bot and waits for its
     * answer. Bots need not answer: a bot that does not answer in time gives [CallbackAnswer.NoAnswer], not an error.
     * A wrong password ([CallbackPayload.DataWithPassword]) fails with `DomainError.PasswordInvalid`.
     */
    suspend fun answer(
        chatId: Long,
        messageId: Long,
        payload: CallbackPayload,
    ): Outcome<CallbackAnswer>

    /** What opening login button [buttonId] of message [messageId] in chat [chatId] needs: a link, or a question. */
    suspend fun loginUrlInfo(
        chatId: Long,
        messageId: Long,
        buttonId: Long,
    ): Outcome<LoginUrlInfo>

    /**
     * The link that logs the user in to the site of login button [buttonId] of message [messageId] in chat [chatId],
     * once they agreed to [LoginUrlInfo.RequestConfirmation]; letting its bot send them messages if [allowWriteAccess].
     */
    suspend fun loginUrl(
        chatId: Long,
        messageId: Long,
        buttonId: Long,
        allowWriteAccess: Boolean,
    ): Outcome<String>

    /**
     * Shares the users [userIds] the user picked for keyboard button [buttonId] of message [messageId] in chat
     * [chatId] with its bot; with [onlyCheck], only checks that they may be shared.
     */
    suspend fun shareUsers(
        chatId: Long,
        messageId: Long,
        buttonId: Int,
        userIds: ImmutableList<Long>,
        onlyCheck: Boolean = false,
    ): Outcome<Unit>

    /**
     * Shares chat [sharedChatId], which the user picked for keyboard button [buttonId] of message [messageId] in chat
     * [chatId], with its bot; with [onlyCheck], only checks that it may be shared.
     */
    suspend fun shareChat(
        chatId: Long,
        messageId: Long,
        buttonId: Int,
        sharedChatId: Long,
        onlyCheck: Boolean = false,
    ): Outcome<Unit>
}
