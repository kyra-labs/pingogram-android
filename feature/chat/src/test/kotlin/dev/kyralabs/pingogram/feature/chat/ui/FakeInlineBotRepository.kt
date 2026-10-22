package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.InlineBot
import dev.kyralabs.pingogram.core.domain.model.InlineQueryResults
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.repository.InlineBotRepository
import kotlinx.collections.immutable.persistentListOf

/**
 * Inline bots as tests set them: the bots by username ([bots], none by default), each query's answer from [onResults]
 * (no results by default), and sends from [onSend]; every lookup, query and send is recorded.
 */
class FakeInlineBotRepository : InlineBotRepository {
    /** The bots by lower-case username; a username missing here names no inline bot. */
    val bots = mutableMapOf<String, InlineBot>()

    /** What a lookup answers instead of [bots], e.g. a failure. */
    var onLookup: (suspend (String) -> Outcome<InlineBot?>)? = null

    var onResults: suspend (Query) -> Outcome<InlineQueryResults> = {
        Outcome.Success(InlineQueryResults(1, null, persistentListOf(), ""))
    }

    var onSend: suspend () -> Outcome<Unit> = { Outcome.Success(Unit) }

    val lookups = mutableListOf<String>()
    val queries = mutableListOf<Query>()
    val sent = mutableListOf<Sent>()

    /** A query as the view model sent it. */
    data class Query(
        val botUserId: Long,
        val chatId: Long,
        val query: String,
        val offset: String,
        val location: Location?,
    )

    /** A result as the view model sent it. */
    data class Sent(
        val chatId: Long,
        val queryId: Long,
        val resultId: String,
        val options: SendOptions,
        val replyToMessageId: Long?,
    )

    override suspend fun inlineBot(username: String): Outcome<InlineBot?> {
        lookups += username
        return onLookup?.invoke(username) ?: Outcome.Success(bots[username.lowercase()])
    }

    override suspend fun results(
        botUserId: Long,
        chatId: Long,
        query: String,
        offset: String,
        location: Location?,
    ): Outcome<InlineQueryResults> {
        val sentQuery = Query(botUserId, chatId, query, offset, location)
        queries += sentQuery
        return onResults(sentQuery)
    }

    override suspend fun send(
        chatId: Long,
        queryId: Long,
        resultId: String,
        options: SendOptions,
        replyToMessageId: Long?,
    ): Outcome<Unit> {
        sent += Sent(chatId, queryId, resultId, options, replyToMessageId)
        return onSend()
    }
}
