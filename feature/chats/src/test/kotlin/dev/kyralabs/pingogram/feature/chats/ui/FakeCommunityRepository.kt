package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Community
import dev.kyralabs.pingogram.core.domain.model.CommunityAddChatRequest
import dev.kyralabs.pingogram.core.domain.model.CommunityAddResult
import dev.kyralabs.pingogram.core.domain.model.CommunityChat
import dev.kyralabs.pingogram.core.domain.model.CommunityDetails
import dev.kyralabs.pingogram.core.domain.model.CommunityRole
import dev.kyralabs.pingogram.core.domain.repository.CommunityRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The communities the user joined ([joinedCommunities]) with their chats and mute ([details]); [setCollapsed] records
 * its calls ([collapsedCalls]) and changes the community, or fails with [collapseError]. While [joinedAnswered] is
 * false, [joined] waits, as for TDLib's answer to `getJoinedCommunities`.
 */
class FakeCommunityRepository : CommunityRepository {
    val joinedCommunities = MutableStateFlow<List<Community>>(emptyList())
    val detailsById = MutableStateFlow<Map<Long, CommunityDetails>>(emptyMap())
    val collapsedCalls = mutableListOf<Pair<Long, Boolean>>()
    var collapseError: DomainError? = null
    val joinedAnswered = MutableStateFlow(true)

    /** Adds community [id], collapsing [chatIds] unless [collapsed] is false. */
    fun add(
        id: Long,
        name: String,
        chatIds: List<Long>,
        collapsed: Boolean = true,
        muted: Boolean = false,
    ) {
        val community =
            Community(
                id = id,
                name = name,
                photo = null,
                hasAccess = true,
                role = CommunityRole.Member,
                canEditChatList = false,
                areChatsCollapsed = collapsed,
            )
        joinedCommunities.value = joinedCommunities.value.filterNot { it.id == id } + community
        val chats = chatIds.map { CommunityChat(it, canViewHistory = true, isHidden = false) }
        detailsById.value = detailsById.value + (id to CommunityDetails(chats.toImmutableList(), 0, isMuted = muted))
    }

    override fun community(id: Long): Flow<Community?> =
        joinedCommunities.map { list -> list.firstOrNull { it.id == id } }

    override fun details(id: Long): Flow<Outcome<CommunityDetails>> =
        detailsById.map { it[id]?.let { details -> Outcome.Success(details) } ?: Outcome.Failure(DomainError.NotFound) }

    override fun joined(): Flow<Outcome<ImmutableList<Community>>> =
        flow {
            joinedAnswered.first { it }
            emitAll(joinedCommunities.map { Outcome.Success(it.toImmutableList()) })
        }

    override suspend fun setCollapsed(
        id: Long,
        collapsed: Boolean,
    ): Outcome<Unit> {
        collapsedCalls += id to collapsed
        collapseError?.let { return Outcome.Failure(it) }
        joinedCommunities.value =
            joinedCommunities.value.map { if (it.id == id) it.copy(areChatsCollapsed = collapsed) else it }
        return Outcome.Success(Unit)
    }

    override suspend fun rename(
        id: Long,
        name: String,
    ): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun delete(id: Long): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun suitableChats(): Outcome<ImmutableList<Long>> = Outcome.Success(persistentListOf())

    override suspend fun addChat(
        id: Long,
        chatId: Long,
        isHidden: Boolean,
    ): Outcome<CommunityAddResult> = Outcome.Success(CommunityAddResult.Added)

    override suspend fun removeChat(
        id: Long,
        chatId: Long,
    ): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun addChatRequests(id: Long): Outcome<ImmutableList<CommunityAddChatRequest>> =
        Outcome.Success(persistentListOf())

    override suspend fun processAddChatRequest(
        id: Long,
        chatId: Long,
        approve: Boolean,
    ): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun setMuted(
        id: Long,
        muted: Boolean,
    ): Outcome<Unit> = Outcome.Success(Unit)
}
