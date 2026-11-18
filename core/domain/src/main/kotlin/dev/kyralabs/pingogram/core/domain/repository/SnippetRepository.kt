package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Snippet
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The account's snippets (`REQUIREMENTS.md` FR-X2), kept on this device only, as bookmarks are: Telegram never sees
 * them, and they go when the account logs out. A name must be [Snippet.isValidName] and no other snippet's, letter case
 * aside; a text must not be blank and at most [Snippet.MAX_TEXT_LENGTH] long; at most [Snippet.MAX_SNIPPETS] are kept.
 * Writes that break these, or whose file cannot be written, fail with `DomainError.Unknown`.
 */
interface SnippetRepository {
    /** Every snippet, in the user's order, then every change. */
    val snippets: Flow<ImmutableList<Snippet>>

    /** Adds a snippet named [name] (trimmed) with [text] at the end of the list, and answers it. */
    suspend fun add(
        name: String,
        text: String,
    ): Outcome<Snippet>

    /** Renames [snippet] or changes its text; it keeps its place. */
    suspend fun update(snippet: Snippet): Outcome<Unit>

    /** Deletes snippet [id]; nothing to do without one. */
    suspend fun remove(id: Long): Outcome<Unit>

    /** Puts the snippets in the order of [ids]; snippets it leaves out keep their order after them. */
    suspend fun reorder(ids: List<Long>): Outcome<Unit>
}
