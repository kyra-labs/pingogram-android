package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toTdMessageSender
import dev.kyralabs.pingogram.core.data.mapper.toTdMessageTopic
import dev.kyralabs.pingogram.core.domain.model.ChatSearchCursor
import dev.kyralabs.pingogram.core.domain.model.ChatSearchPage
import dev.kyralabs.pingogram.core.domain.model.ChatSearchQuery
import dev.kyralabs.pingogram.core.domain.repository.ChatSearchRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatSearchRepository.Companion.PAGE_SIZE
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import java.time.Instant
import javax.inject.Inject

/**
 * [ChatSearchRepository] on TDLib, without a cache: each search is asked for when the user types or pages.
 *
 * - **Messages**: `searchChatMessages` with the query's words, sender and topic, paged by `next_from_message_id`
 *   (offset 0, so a page starts right after the last one). A secret chat's messages are on the device only, which
 *   `searchChatMessages` does not search with words: `searchSecretMessages`, paged by its offset, and without a
 *   sender, which it does not filter by. TDLib answers 400 for a query it cannot search; that finds nothing.
 * - **A day**: `getChatMessageByDate` gives the last message before the day; the first message after it is the next
 *   newer one (`getChatHistory` from it with offset -1). Without any message before the day, the last one up to its
 *   end is shown, which is then the day's. `getChatMessageCalendar` would count the messages of each day, but only of
 *   one kind (photos, videos…): td_api.tl does not accept it for every message, so it cannot say which days have any.
 */
@AccountScoped
class TdlibChatSearchRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : ChatSearchRepository {
        override suspend fun search(
            chatId: Long,
            query: ChatSearchQuery,
            cursor: ChatSearchCursor?,
        ): Outcome<ChatSearchPage> {
            if (query.isEmpty) return Outcome.Success(NOTHING)
            return if (query.isSecretChat) {
                secretChat(
                    chatId,
                    query.text.trim(),
                    cursor,
                )
            } else {
                chat(chatId, query, cursor)
            }
        }

        override suspend fun messageForDay(
            chatId: Long,
            start: Instant,
            end: Instant,
        ): Outcome<Long> =
            when (val before = client.request(TdApi.GetChatMessageByDate(chatId, start.unixTime() - 1))) {
                is Outcome.Success -> {
                    val last = before.value.id
                    // The message before the day and the one after it, newest first.
                    client.request(TdApi.GetChatHistory(chatId, last, -1, 2, false)).map { history ->
                        history.messages
                            .filterNotNull()
                            .map { it.id }
                            .filter { it > last }
                            .minOrNull() ?: last
                    }
                }

                is Outcome.Failure -> {
                    if (before.error == DomainError.NotFound) {
                        client.request(TdApi.GetChatMessageByDate(chatId, end.unixTime() - 1)).map { it.id }
                    } else {
                        before
                    }
                }
            }

        private suspend fun chat(
            chatId: Long,
            query: ChatSearchQuery,
            cursor: ChatSearchCursor?,
        ): Outcome<ChatSearchPage> {
            val from = (cursor as? ChatSearchCursor.FromMessage)?.messageId ?: 0L
            val request =
                TdApi.SearchChatMessages(
                    chatId,
                    query.topic?.toTdMessageTopic(),
                    query.text.trim(),
                    query.sender?.toTdMessageSender(),
                    from,
                    0,
                    PAGE_SIZE,
                    null,
                )
            return client.request(request).orNothing().map { found ->
                ChatSearchPage(
                    messageIds =
                        found.messages
                            .filterNotNull()
                            .map { it.id }
                            .toImmutableList(),
                    totalCount = found.totalCount.takeIf { it >= 0 },
                    next = found.nextFromMessageId.takeIf { it != 0L }?.let(ChatSearchCursor::FromMessage),
                )
            }
        }

        private suspend fun secretChat(
            chatId: Long,
            text: String,
            cursor: ChatSearchCursor?,
        ): Outcome<ChatSearchPage> {
            if (text.isEmpty()) return Outcome.Success(NOTHING)
            val offset = (cursor as? ChatSearchCursor.Offset)?.offset.orEmpty()
            val request = TdApi.SearchSecretMessages(chatId, text, offset, PAGE_SIZE, null)
            return client.request(request).orNothingFound().map { found ->
                ChatSearchPage(
                    messageIds =
                        found.messages
                            .filterNotNull()
                            .map { it.id }
                            .toImmutableList(),
                    totalCount = found.totalCount.takeIf { it >= 0 },
                    next = found.nextOffset.takeIf { it.isNotEmpty() }?.let(ChatSearchCursor::Offset),
                )
            }
        }
    }

/** A search that found nothing. */
private val NOTHING = ChatSearchPage(persistentListOf(), totalCount = 0, next = null)

private const val BAD_REQUEST = 400

/** What TDLib could not search (a 400) found nothing instead of failing. */
private fun Outcome<TdApi.FoundChatMessages>.orNothing(): Outcome<TdApi.FoundChatMessages> =
    if (isBadRequest()) Outcome.Success(TdApi.FoundChatMessages(0, emptyArray(), 0)) else this

private fun Outcome<TdApi.FoundMessages>.orNothingFound(): Outcome<TdApi.FoundMessages> =
    if (isBadRequest()) Outcome.Success(TdApi.FoundMessages(0, emptyArray(), "")) else this

private fun Outcome<*>.isBadRequest(): Boolean =
    ((this as? Outcome.Failure)?.error as? DomainError.Unknown)?.code == BAD_REQUEST

/** The Unix time of this instant, in seconds, as TDLib's dates are. */
private fun Instant.unixTime(): Int = epochSecond.toInt()
