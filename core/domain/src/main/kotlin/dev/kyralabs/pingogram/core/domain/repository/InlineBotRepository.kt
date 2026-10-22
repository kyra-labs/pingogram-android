package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.InlineBot
import dev.kyralabs.pingogram.core.domain.model.InlineQueryResults
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.core.domain.model.SendOptions

/**
 * Inline bots (`REQUIREMENTS.md` FR-C5, "@gif cats"): finding the bot a username names, asking it for results and
 * sending one. Every request comes from what the user typed or tapped; nothing is asked of a bot ahead of time. Nothing
 * is cached here: TDLib keeps the users it resolved and the answers of recent queries.
 *
 * Requests that get no answer in time fail with `DomainError.Network`.
 */
interface InlineBotRepository {
    /**
     * The inline bot with [username] (without "@"), as TDLib resolves it (`searchPublicChat`, from memory for a
     * username resolved before): null where nobody has the username, or its owner is no bot that answers inline
     * queries.
     */
    suspend fun inlineBot(username: String): Outcome<InlineBot?>

    /**
     * The page at [offset] (empty for the first) of bot [botUserId]'s results for [query] typed in chat [chatId]
     * (td_api.tl `getInlineQueryResults`), with the user's [location] where the user agreed to share it with a bot that
     * asks for it; null sends none. Cancelling the call drops a late answer.
     */
    suspend fun results(
        botUserId: Long,
        chatId: Long,
        query: String,
        offset: String = "",
        location: Location? = null,
    ): Outcome<InlineQueryResults>

    /**
     * Sends result [resultId] of query [queryId] into chat [chatId] (td_api.tl `sendInlineQueryResultMessage`), with
     * [options]' notification, scheduling and topic, replying to message [replyToMessageId] of the chat if not null;
     * the message says which bot it came from. TDLib puts it into the chat as being sent before it answers.
     */
    suspend fun send(
        chatId: Long,
        queryId: Long,
        resultId: String,
        options: SendOptions = SendOptions(),
        replyToMessageId: Long? = null,
    ): Outcome<Unit>
}
