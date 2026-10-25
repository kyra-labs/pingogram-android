package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toInlineQueryResults
import dev.kyralabs.pingogram.core.data.mapper.toInputMessageReplyTo
import dev.kyralabs.pingogram.core.domain.model.InlineBot
import dev.kyralabs.pingogram.core.domain.model.InlineQueryResults
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.repository.InlineBotRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [InlineBotRepository] on TDLib. No cache, so no `TdlibUpdateCache`: TDLib keeps the users it resolved, and answers a
 * query it was asked recently from its own cache.
 *
 * - **The bot**: `searchPublicChat` of the username (from memory once resolved), then `getUser` of a private chat's
 *   user, whose `userTypeBot.is_inline` says whether it answers inline queries. A username nobody has is a 400
 *   (`USERNAME_NOT_OCCUPIED`, `USERNAME_INVALID`), which is no inline bot rather than a failure.
 * - **Results**: `getInlineQueryResults`, with the location only where the caller passes one (the user agreed).
 * - **Sending**: `sendInlineQueryResultMessage` with the bot named ("via @gif", `hide_via_bot` false, as Telegram asks
 *   of clients); the message arrives through `updateNewMessage` before the answer, which is not applied.
 *
 * Neither the queries nor the results are logged.
 */
@Singleton
class TdlibInlineBotRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : InlineBotRepository {
        override suspend fun inlineBot(username: String): Outcome<InlineBot?> {
            val name = username.trim().removePrefix("@")
            if (name.isEmpty()) return Outcome.Success(null)
            return when (val found = client.request(TdApi.SearchPublicChat(name))) {
                is Outcome.Success -> botOf(found.value, name)
                is Outcome.Failure -> found.error.asNoBot()
            }
        }

        /** The inline bot whose private chat [chat] is, typed as [name]; null for any other chat or user. */
        private suspend fun botOf(
            chat: TdApi.Chat,
            name: String,
        ): Outcome<InlineBot?> {
            val userId = (chat.type as? TdApi.ChatTypePrivate)?.userId ?: return Outcome.Success(null)
            return client.request(TdApi.GetUser(userId)).map { user ->
                (user.type as? TdApi.UserTypeBot)?.takeIf { it.isInline }?.let { bot ->
                    InlineBot(
                        userId = userId,
                        chatId = chat.id,
                        username =
                            user.usernames
                                ?.activeUsernames
                                ?.firstOrNull { it.equals(name, ignoreCase = true) } ?: name,
                        placeholder = bot.inlineQueryPlaceholder.orEmpty(),
                        needsLocation = bot.needLocation,
                    )
                }
            }
        }

        override suspend fun results(
            botUserId: Long,
            chatId: Long,
            query: String,
            offset: String,
            location: Location?,
        ): Outcome<InlineQueryResults> {
            val userLocation = location?.let { TdApi.Location(it.latitude, it.longitude, 0.0) }
            return client
                .request(TdApi.GetInlineQueryResults(botUserId, chatId, userLocation, query, offset))
                .map { it.toInlineQueryResults() }
        }

        override suspend fun send(
            chatId: Long,
            queryId: Long,
            resultId: String,
            options: SendOptions,
            replyTo: ReplyTarget?,
        ): Outcome<Unit> {
            val send =
                TdApi.SendInlineQueryResultMessage(
                    chatId,
                    options.tdTopic(),
                    replyTo?.toInputMessageReplyTo(),
                    options.toTdOptions(),
                    queryId,
                    resultId,
                    false,
                )
            return client.request(send).map {}
        }

        /**
         * A username that nobody has, or that cannot be one (any other 400), names no inline bot; other failures stay
         * failures.
         */
        private fun DomainError.asNoBot(): Outcome<InlineBot?> {
            val noBot =
                this == DomainError.NotFound ||
                    this == DomainError.UsernameInvalid ||
                    (this as? DomainError.Unknown)?.code == BAD_REQUEST
            return if (noBot) Outcome.Success(null) else Outcome.Failure(this)
        }
    }

private const val BAD_REQUEST = 400
