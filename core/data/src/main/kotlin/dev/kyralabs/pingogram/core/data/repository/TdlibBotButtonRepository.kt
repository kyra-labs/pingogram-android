package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.error.toOutcome
import dev.kyralabs.pingogram.core.data.mapper.toTdPayload
import dev.kyralabs.pingogram.core.domain.model.CallbackAnswer
import dev.kyralabs.pingogram.core.domain.model.CallbackPayload
import dev.kyralabs.pingogram.core.domain.model.LoginUrlInfo
import dev.kyralabs.pingogram.core.domain.repository.BotButtonRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [BotButtonRepository] on `getCallbackQueryAnswer`, `getLoginUrlInfo`, `getLoginUrl`, `shareUsersWithBot` and
 * `shareChatWithBot`, without a cache: each answers one tap.
 *
 * A bot need not answer a callback query: TDLib then fails it with 502 once Telegram gave up waiting
 * (`BOT_RESPONSE_TIMEOUT`), which is [CallbackAnswer.NoAnswer], as is no answer within [REQUEST_TIMEOUT], so the
 * button's progress just ends.
 */
@AccountScoped
class TdlibBotButtonRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : BotButtonRepository {
        override suspend fun answer(
            chatId: Long,
            messageId: Long,
            payload: CallbackPayload,
        ): Outcome<CallbackAnswer> {
            val result =
                withTimeoutOrNull(REQUEST_TIMEOUT) {
                    client.send(TdApi.GetCallbackQueryAnswer(chatId, messageId, payload.toTdPayload()))
                } ?: return Outcome.Success(CallbackAnswer.NoAnswer)
            return if (result is TdResult.Failure && result.error.isNoAnswer) {
                Outcome.Success(CallbackAnswer.NoAnswer)
            } else {
                result.toOutcome().map { CallbackAnswer.Answered(it.text.orEmpty(), it.showAlert, it.url.orEmpty()) }
            }
        }

        override suspend fun loginUrlInfo(
            chatId: Long,
            messageId: Long,
            buttonId: Long,
        ): Outcome<LoginUrlInfo> =
            client.request(TdApi.GetLoginUrlInfo(chatId, messageId, buttonId)).map { info ->
                when (info) {
                    is TdApi.LoginUrlInfoRequestConfirmation -> {
                        LoginUrlInfo.RequestConfirmation(
                            url = info.url,
                            domain = info.domain,
                            botUserId = info.botUserId,
                            requestWriteAccess = info.requestWriteAccess,
                        )
                    }

                    is TdApi.LoginUrlInfoOpen -> {
                        LoginUrlInfo.Open(info.url, info.skipConfirmation)
                    }

                    // A kind a newer TDLib adds: nothing to open.
                    else -> {
                        LoginUrlInfo.Open(url = "", skipConfirmation = false)
                    }
                }
            }

        override suspend fun loginUrl(
            chatId: Long,
            messageId: Long,
            buttonId: Long,
            allowWriteAccess: Boolean,
        ): Outcome<String> =
            client.request(TdApi.GetLoginUrl(chatId, messageId, buttonId, allowWriteAccess)).map { it.url }

        override suspend fun shareUsers(
            chatId: Long,
            messageId: Long,
            buttonId: Int,
            userIds: ImmutableList<Long>,
            onlyCheck: Boolean,
        ): Outcome<Unit> =
            client
                .request(
                    TdApi.ShareUsersWithBot(
                        TdApi.KeyboardButtonSourceMessage(chatId, messageId),
                        buttonId,
                        userIds.toLongArray(),
                        onlyCheck,
                    ),
                ).map {}

        override suspend fun shareChat(
            chatId: Long,
            messageId: Long,
            buttonId: Int,
            sharedChatId: Long,
            onlyCheck: Boolean,
        ): Outcome<Unit> =
            client
                .request(
                    TdApi.ShareChatWithBot(
                        TdApi.KeyboardButtonSourceMessage(chatId, messageId),
                        buttonId,
                        sharedChatId,
                        onlyCheck,
                    ),
                ).map {}
    }

/** TDLib's answer when the bot did not answer a callback query in time. */
private val TdError.isNoAnswer: Boolean
    get() = code == BOT_TIMEOUT || message.contains("BOT_RESPONSE_TIMEOUT")

/** td_api.tl `getCallbackQueryAnswer`: "Returns an error with code 502 if the bot fails to answer the query". */
private const val BOT_TIMEOUT = 502
