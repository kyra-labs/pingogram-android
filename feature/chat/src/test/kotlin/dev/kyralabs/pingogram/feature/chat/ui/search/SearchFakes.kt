package dev.kyralabs.pingogram.feature.chat.ui.search

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatSearchCursor
import dev.kyralabs.pingogram.core.domain.model.ChatSearchPage
import dev.kyralabs.pingogram.core.domain.model.ChatSearchQuery
import dev.kyralabs.pingogram.core.domain.model.GroupInfo
import dev.kyralabs.pingogram.core.domain.model.GroupMember
import dev.kyralabs.pingogram.core.domain.model.GroupMembersPage
import dev.kyralabs.pingogram.core.domain.model.MemberFilter
import dev.kyralabs.pingogram.core.domain.repository.ChatSearchRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupInfoRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant

/** A search in one chat: what was asked, and the answers the test gives. */
internal class FakeChatSearchRepository : ChatSearchRepository {
    /** One search asked for. */
    data class Search(
        val chatId: Long,
        val query: ChatSearchQuery,
        val cursor: ChatSearchCursor?,
    )

    val searches = mutableListOf<Search>()

    /** The days asked for: their starts and ends. */
    val days = mutableListOf<Pair<Instant, Instant>>()

    /** The answer to a search; nothing found by default. */
    var answer: suspend (ChatSearchQuery, ChatSearchCursor?) -> Outcome<ChatSearchPage> = { _, _ -> found() }

    /** The answer to a day; not found by default. */
    var dayAnswer: suspend (Instant, Instant) -> Outcome<Long> = { _, _ -> Outcome.Failure(DomainError.NotFound) }

    override suspend fun search(
        chatId: Long,
        query: ChatSearchQuery,
        cursor: ChatSearchCursor?,
    ): Outcome<ChatSearchPage> {
        searches += Search(chatId, query, cursor)
        return answer(query, cursor)
    }

    override suspend fun messageForDay(
        chatId: Long,
        start: Instant,
        end: Instant,
    ): Outcome<Long> {
        days += start to end
        return dayAnswer(start, end)
    }

    companion object {
        /** A page of [ids], about [total] in all, the next one from [next] if not null. */
        fun found(
            vararg ids: Long,
            total: Int? = ids.size,
            next: Long? = null,
        ): Outcome<ChatSearchPage> =
            Outcome.Success(
                ChatSearchPage(ids.toList().toImmutableList(), total, next?.let(ChatSearchCursor::FromMessage)),
            )
    }
}

/** A group's members for the search's sheet: what was asked, and the answer the test gives. */
internal class FakeGroupInfoRepository : GroupInfoRepository {
    /** The queries asked for, in order. */
    val queries = mutableListOf<String>()

    var answer: suspend (String) -> Outcome<GroupMembersPage> = { Outcome.Success(page()) }

    override fun groupInfo(chatId: Long): Flow<Outcome<GroupInfo>> = emptyFlow()

    override suspend fun members(
        chatId: Long,
        filter: MemberFilter,
        query: String,
        offset: Int,
        limit: Int,
    ): Outcome<GroupMembersPage> {
        queries += query
        return answer(query)
    }

    override suspend fun member(
        chatId: Long,
        userId: Long,
    ): Outcome<GroupMember> = Outcome.Failure(DomainError.NotFound)

    companion object {
        fun page(vararg members: GroupMember) = GroupMembersPage(members.toList().toImmutableList(), members.size)
    }
}
