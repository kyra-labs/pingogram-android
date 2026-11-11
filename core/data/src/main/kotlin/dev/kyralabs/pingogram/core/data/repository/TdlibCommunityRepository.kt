package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toCommunity
import dev.kyralabs.pingogram.core.data.mapper.toCommunityDetails
import dev.kyralabs.pingogram.core.domain.model.Community
import dev.kyralabs.pingogram.core.domain.model.CommunityDetails
import dev.kyralabs.pingogram.core.domain.repository.CommunityRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [CommunityRepository] on TDLib's communities. TDLib announces each community the account knows with
 * `updateCommunity` and has no function to read one again, so every community is kept for the session, as the
 * supergroups are. What a community holds is TDLib's full info, asked for while someone collects it and followed
 * through `updateCommunityFullInfo`.
 */
@AccountScoped
class TdlibCommunityRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : CommunityRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, persistentMapOf(), ::reduce)

        override fun community(id: Long): Flow<Community?> = store.state.map { it[id] }.distinctUntilChanged()

        override fun details(id: Long): Flow<Outcome<CommunityDetails>> =
            channelFlow {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    client.updates
                        .filterIsInstance<TdApi.UpdateCommunityFullInfo>()
                        .filter { it.communityId == id && it.communityFullInfo != null }
                        .collect { send(Outcome.Success(it.communityFullInfo.toCommunityDetails())) }
                }
                send(client.request(TdApi.GetCommunityFullInfo(id)).map { it.toCommunityDetails() })
            }

        override suspend fun rename(
            id: Long,
            name: String,
        ): Outcome<Unit> {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return Outcome.Failure(DomainError.TitleInvalid)
            return client.request(TdApi.SetCommunityName(id, trimmed)).map {}
        }

        override suspend fun delete(id: Long): Outcome<Unit> = client.request(TdApi.DeleteCommunity(id)).map {}
    }

/** The communities with [update] applied; the same map for other updates and for a community that is unchanged. */
private fun reduce(
    communities: PersistentMap<Long, Community>,
    update: TdApi.Update,
): PersistentMap<Long, Community> {
    if (update !is TdApi.UpdateCommunity) return communities
    val community = update.community.toCommunity()
    return if (communities[community.id] == community) communities else communities.putting(community.id, community)
}
