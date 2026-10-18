package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.data.mapper.toBasicGroup
import dev.kyralabs.pingogram.core.data.mapper.toSupergroup
import dev.kyralabs.pingogram.core.domain.model.BasicGroup
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [GroupRepository] on TDLib's basic groups and supergroups. TDLib announces each with `updateBasicGroup` or
 * `updateSupergroup` before any of its ids reaches the app, and sends the whole group again whenever it changes, so
 * every group is kept for the session: as many as the chats, messages and searches TDLib loaded.
 *
 * A group that changed only in fields Pingogram does not keep (boost level, stories, …) keeps its map, so the
 * whole-map flows emit only real changes.
 */
@Singleton
class TdlibGroupRepository
    @Inject
    constructor(
        client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : GroupRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, GroupCache(), GroupCache::reduce)

        override val basicGroups: Flow<ImmutableMap<Long, BasicGroup>> =
            store.state.map { it.basicGroups }.distinctUntilChanged { old, new -> old === new }

        override fun basicGroup(id: Long): Flow<BasicGroup?> =
            store.state.map { it.basicGroups[id] }.distinctUntilChanged()

        override val supergroups: Flow<ImmutableMap<Long, Supergroup>> =
            store.state.map { it.supergroups }.distinctUntilChanged { old, new -> old === new }

        override fun supergroup(id: Long): Flow<Supergroup?> =
            store.state.map { it.supergroups[id] }.distinctUntilChanged()
    }

private data class GroupCache(
    val basicGroups: PersistentMap<Long, BasicGroup> = persistentMapOf(),
    val supergroups: PersistentMap<Long, Supergroup> = persistentMapOf(),
) {
    fun reduce(update: TdApi.Update): GroupCache =
        when (update) {
            is TdApi.UpdateBasicGroup -> {
                val group = update.basicGroup.toBasicGroup()
                if (basicGroups[group.id] == group) this else copy(basicGroups = basicGroups.putting(group.id, group))
            }

            is TdApi.UpdateSupergroup -> {
                val group = update.supergroup.toSupergroup()
                if (supergroups[group.id] == group) this else copy(supergroups = supergroups.putting(group.id, group))
            }

            else -> {
                this
            }
        }
}
