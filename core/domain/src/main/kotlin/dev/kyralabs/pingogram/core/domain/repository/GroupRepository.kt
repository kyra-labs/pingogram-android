package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.BasicGroup
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.Flow

/**
 * The basic groups, supergroups and channels TDLib announced in this session, kept in memory
 * (`docs/ARCHITECTURE.md`, Repositories): the badges, names and membership behind the chats of groups and channels.
 * Everything is dropped when TDLib closes (logout).
 */
interface GroupRepository {
    /**
     * Every basic group by id, then the map after every change of any of them. For lists of chats, which look their
     * groups up here instead of observing each one.
     */
    val basicGroups: Flow<ImmutableMap<Long, BasicGroup>>

    /** The basic group with [id], then every change; null while TDLib has not announced it. */
    fun basicGroup(id: Long): Flow<BasicGroup?>

    /** Every supergroup and channel by id, then the map after every change of any of them; see [basicGroups]. */
    val supergroups: Flow<ImmutableMap<Long, Supergroup>>

    /** The supergroup or channel with [id], then every change; null while TDLib has not announced it. */
    fun supergroup(id: Long): Flow<Supergroup?>
}
