package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.FoundMessages
import dev.kyralabs.pingogram.core.domain.model.MessageSearchFilter
import dev.kyralabs.pingogram.core.domain.model.UsernameOwner
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * [SearchRepository] for the tests of [SearchViewModel] and [NewChatViewModel]: each search answers with its handler,
 * finding nothing by default, and every call is recorded ([calls]) as `chats:<query>`, `contacts:<query>`,
 * `public:<query>`, `messages:<query>:<offset>` (with `:<filter>` after it for photos and videos or links),
 * `allContacts`, `recents`, `top`, `add:<id>`, `remove:<id>` or `clear`; calls cancelled while they wait are recorded
 * in [cancelled] too. The recent chats are a list the changes act on.
 */
class FakeSearchRepository : SearchRepository {
    val calls = mutableListOf<String>()
    val cancelled = mutableListOf<String>()

    var onSearchChats: (String) -> Flow<Outcome<ImmutableList<Chat>>> = { flow { emit(found()) } }
    var onSearchContacts: suspend (String) -> Outcome<ImmutableList<Chat>> = { found() }
    var onSearchPublicChats: suspend (String) -> Outcome<ImmutableList<Chat>> = { found() }
    var onContacts: suspend () -> Outcome<ImmutableList<Chat>> = { found() }
    var onSearchMessages: suspend (String, String?) -> Outcome<FoundMessages> = { _, _ ->
        Outcome.Success(FoundMessages(persistentListOf(), nextOffset = null, totalCount = null))
    }

    /** The answer to adding, removing and clearing recent chats, by call; succeeds by default. */
    var onChangeRecents: suspend (String) -> Outcome<Unit> = { Outcome.Success(Unit) }

    val recents = mutableListOf<Chat>()
    var topPeople: List<Chat> = emptyList()

    /** Every call of [prefix] ("contacts", "messages", …), in order. */
    fun callsOf(prefix: String) = calls.filter { it.startsWith("$prefix:") || it == prefix }

    override fun searchChats(query: String): Flow<Outcome<ImmutableList<Chat>>> =
        flow {
            record("chats:$query") { onSearchChats(query).collect { emit(it) } }
        }

    override suspend fun searchContacts(query: String) = record("contacts:$query") { onSearchContacts(query) }

    override suspend fun searchPublicChats(query: String) = record("public:$query") { onSearchPublicChats(query) }

    override suspend fun contacts() = record("allContacts") { onContacts() }

    override suspend fun publicChat(username: String): Outcome<UsernameOwner> =
        record("username:$username") { Outcome.Failure(DomainError.NotFound) }

    override suspend fun searchMessages(
        query: String,
        offset: String?,
        filter: MessageSearchFilter,
    ): Outcome<FoundMessages> {
        val call = "messages:$query:$offset" + if (filter == MessageSearchFilter.All) "" else ":$filter"
        return record(call) { onSearchMessages(query, offset) }
    }

    override suspend fun recentChats() = record("recents") { Outcome.Success(recents.toImmutableList()) }

    override suspend fun addRecentChat(chatId: Long) = change("add:$chatId")

    override suspend fun removeRecentChat(chatId: Long): Outcome<Unit> {
        val result = change("remove:$chatId")
        if (result is Outcome.Success) recents.removeAll { it.id == chatId }
        return result
    }

    override suspend fun clearRecentChats(): Outcome<Unit> {
        val result = change("clear")
        if (result is Outcome.Success) recents.clear()
        return result
    }

    override suspend fun topPeople() = record("top") { Outcome.Success(topPeople.toImmutableList()) }

    private suspend fun change(call: String) = record(call) { onChangeRecents(call) }

    private suspend fun <T> record(
        call: String,
        block: suspend () -> T,
    ): T {
        calls += call
        return try {
            block()
        } catch (cancellation: CancellationException) {
            cancelled += call
            throw cancellation
        }
    }

    companion object {
        /** Found [chats]. */
        fun found(vararg chats: Chat): Outcome<ImmutableList<Chat>> = Outcome.Success(chats.toList().toImmutableList())
    }
}
