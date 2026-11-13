package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.MUTE_FOREVER
import dev.kyralabs.pingogram.core.data.mapper.toAddChatRequest
import dev.kyralabs.pingogram.core.data.mapper.toCommunity
import dev.kyralabs.pingogram.core.data.mapper.toCommunityDetails
import dev.kyralabs.pingogram.core.domain.model.Community
import dev.kyralabs.pingogram.core.domain.model.CommunityAddChatRequest
import dev.kyralabs.pingogram.core.domain.model.CommunityAddResult
import dev.kyralabs.pingogram.core.domain.model.CommunityDetails
import dev.kyralabs.pingogram.core.domain.model.isJoined
import dev.kyralabs.pingogram.core.domain.repository.CommunityRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [CommunityRepository] on TDLib's communities. TDLib announces each community the account knows with
 * `updateCommunity` and has no function to read one again, so every community is kept for the session, as the
 * supergroups are. What a community holds is TDLib's full info, asked for while someone collects it and followed
 * through `updateCommunityFullInfo`.
 *
 * The communities the user joined are `getJoinedCommunities` once per collection, which makes TDLib announce them,
 * then the store: a community TDLib announces the user in later joins the list, one they left drops out. What was read
 * before in the session ([KnownCommunities]: the last order and each community's last full info) comes first, so a
 * screen that opens again shows it at once rather than after TDLib answers. A request to
 * add a chat that administrators approve comes back as TDLib's error `COMMUNITY_REQUEST_CREATED`, which is
 * [CommunityAddResult.RequestSent].
 */
@Suppress("TooManyFunctions") // One function per request TDLib offers for a community.
@AccountScoped
class TdlibCommunityRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : CommunityRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, persistentMapOf(), ::reduce)

        private val known = UpdateStore(client, scope, KnownCommunities(), ::reduceKnown)

        override fun community(id: Long): Flow<Community?> = store.state.map { it[id] }.distinctUntilChanged()

        override fun details(id: Long): Flow<Outcome<CommunityDetails>> =
            channelFlow {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    client.updates
                        .filterIsInstance<TdApi.UpdateCommunityFullInfo>()
                        .filter { it.communityId == id && it.communityFullInfo != null }
                        .collect { send(Outcome.Success(it.communityFullInfo.toCommunityDetails())) }
                }
                known.state.value.details[id]
                    ?.let { send(Outcome.Success(it)) }
                val details = client.request(TdApi.GetCommunityFullInfo(id)).map { it.toCommunityDetails() }
                if (details is Outcome.Success) known.update { it.copy(details = it.details.put(id, details.value)) }
                send(details)
            }.distinctUntilChanged()

        override suspend fun rename(
            id: Long,
            name: String,
        ): Outcome<Unit> {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return Outcome.Failure(DomainError.TitleInvalid)
            return client.request(TdApi.SetCommunityName(id, trimmed)).map {}
        }

        override suspend fun delete(id: Long): Outcome<Unit> = client.request(TdApi.DeleteCommunity(id)).map {}

        override fun joined(): Flow<Outcome<ImmutableList<Community>>> =
            flow {
                emit(Outcome.Success(store.state.value.joinedIn(known.state.value.order)))
                when (val ids = client.request(TdApi.GetJoinedCommunities())) {
                    is Outcome.Failure -> {
                        emit(ids)
                    }

                    is Outcome.Success -> {
                        val order = (ids.value.communityIds ?: LongArray(0)).toList()
                        known.update { it.copy(order = order) }
                        emitAll(store.state.map { Outcome.Success(it.joinedIn(order)) })
                    }
                }
            }.distinctUntilChanged()

        override suspend fun suitableChats(): Outcome<ImmutableList<Long>> =
            client.request(TdApi.GetSuitableCommunityChats()).map {
                (it.chatIds ?: LongArray(0)).toList().toImmutableList()
            }

        override suspend fun addChat(
            id: Long,
            chatId: Long,
            isHidden: Boolean,
        ): Outcome<CommunityAddResult> =
            when (val added = client.request(TdApi.AddCommunityChat(id, chatId, isHidden))) {
                is Outcome.Success -> {
                    Outcome.Success(CommunityAddResult.Added)
                }

                is Outcome.Failure -> {
                    val error = added.error
                    if (error is DomainError.Unknown && error.message.startsWith(REQUEST_CREATED)) {
                        Outcome.Success(CommunityAddResult.RequestSent)
                    } else {
                        added
                    }
                }
            }

        override suspend fun removeChat(
            id: Long,
            chatId: Long,
        ): Outcome<Unit> = client.request(TdApi.RemoveCommunityChat(id, chatId)).map {}

        override suspend fun addChatRequests(id: Long): Outcome<ImmutableList<CommunityAddChatRequest>> =
            client.request(TdApi.GetCommunityAddChatRequests(id, "", REQUESTS_LIMIT)).map { requests ->
                requests.requests
                    .orEmpty()
                    .map { it.toAddChatRequest() }
                    .toImmutableList()
            }

        override suspend fun processAddChatRequest(
            id: Long,
            chatId: Long,
            approve: Boolean,
        ): Outcome<Unit> = client.request(TdApi.ProcessCommunityAddChatRequest(id, chatId, approve)).map {}

        override suspend fun setMuted(
            id: Long,
            muted: Boolean,
        ): Outcome<Unit> =
            when (val info = client.request(TdApi.GetCommunityFullInfo(id))) {
                is Outcome.Failure -> {
                    info
                }

                is Outcome.Success -> {
                    // The request replaces every field, so the others are sent back as Telegram has them.
                    val settings = info.value.notificationSettings ?: TdApi.ChatNotificationSettings().defaults()
                    settings.useDefaultMuteFor = !muted
                    settings.muteFor = if (muted) MUTE_FOREVER else 0
                    client.request(TdApi.SetCommunityNotificationSettings(id, settings)).map {}
                }
            }

        override suspend fun setCollapsed(
            id: Long,
            collapsed: Boolean,
        ): Outcome<Unit> = client.request(TdApi.ToggleCommunityAreChatsCollapsed(id, collapsed)).map {}
    }

/**
 * The communities of this map the user is in: those of [order] (`getJoinedCommunities`) first, in its order, then the
 * others by id, so one joined since comes last.
 */
private fun Map<Long, Community>.joinedIn(order: List<Long>): ImmutableList<Community> {
    val first = order.mapNotNull { this[it] }.filter { it.role.isJoined }
    val listed = order.toSet()
    val later = values.filter { it.id !in listed && it.role.isJoined }.sortedBy { it.id }
    return (first + later).toImmutableList()
}

/** Every field following its default, for a community whose settings TDLib did not send. */
private fun TdApi.ChatNotificationSettings.defaults(): TdApi.ChatNotificationSettings =
    also {
        it.useDefaultMuteFor = true
        it.useDefaultSound = true
        it.useDefaultShowPreview = true
        it.useDefaultMuteStories = true
        it.useDefaultStorySound = true
        it.useDefaultShowStoryPoster = true
        it.useDefaultDisablePinnedMessageNotifications = true
        it.useDefaultDisableMentionNotifications = true
    }

/** Telegram's answer to adding a chat the user may not add themselves: its administrators got a request. */
private const val REQUEST_CREATED = "COMMUNITY_REQUEST_CREATED"

/** The requests to add a chat read at once, as many as `getCommunityAddChatRequests` gives. */
private const val REQUESTS_LIMIT = 100

/** The communities with [update] applied; the same map for other updates and for a community that is unchanged. */
private fun reduce(
    communities: PersistentMap<Long, Community>,
    update: TdApi.Update,
): PersistentMap<Long, Community> {
    if (update !is TdApi.UpdateCommunity) return communities
    val community = update.community.toCommunity()
    return if (communities[community.id] == community) communities else communities.putting(community.id, community)
}

/**
 * What the repository read before in this session: the last order of the communities the user joined
 * (`getJoinedCommunities`) and each community's last full info (its answer or `updateCommunityFullInfo`).
 */
private data class KnownCommunities(
    val order: List<Long> = emptyList(),
    val details: PersistentMap<Long, CommunityDetails> = persistentMapOf(),
)

/** [known] with the full info of an `updateCommunityFullInfo`; the same for other updates. */
private fun reduceKnown(
    known: KnownCommunities,
    update: TdApi.Update,
): KnownCommunities {
    if (update !is TdApi.UpdateCommunityFullInfo || update.communityFullInfo == null) return known
    return known.copy(details = known.details.put(update.communityId, update.communityFullInfo.toCommunityDetails()))
}
