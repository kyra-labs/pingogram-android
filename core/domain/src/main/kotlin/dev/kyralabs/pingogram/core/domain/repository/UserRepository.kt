package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.User
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The users TDLib announced in this session, kept in memory (`docs/ARCHITECTURE.md`, Repositories), and the id of
 * the logged-in user. Everything is dropped when TDLib closes (logout).
 */
interface UserRepository {
    /** The logged-in user's id, once TDLib reported it; null before login and after logout. */
    val myUserId: StateFlow<Long?>

    /**
     * Every user by id, then the map after every change of any user. For lists that show many users (names in chat
     * list previews, online dots, badges), which look them up here instead of observing each one.
     */
    val users: Flow<ImmutableMap<Long, User>>

    /**
     * Every user by id as [users] has them, but changing only when a user's profile does (name, photo, type, badges),
     * not their online status, which changes far more often: for screens that show no status. A user's status here is
     * the one it had at its last profile change; read [users], [users] of some ids or [user] for the live one.
     */
    val profiles: Flow<ImmutableMap<Long, User>> get() = users

    /**
     * The users among [ids] TDLib announced, by id, then the map whenever one of them changes, status included: for a
     * screen that shows some users' status (members, a chat's info), without following every other user.
     */
    fun users(ids: Set<Long>): Flow<ImmutableMap<Long, User>> =
        users.map { all -> all.pick(ids) }.distinctUntilChanged()

    /** The user with [id], then every change; null while TDLib has not announced the user (see [getUser]). */
    fun user(id: Long): Flow<User?>

    /**
     * The user with [id] from memory, else from TDLib: `DomainError.NotFound` for a user it does not know. A request
     * that gets no answer in time fails with `DomainError.Network`.
     */
    suspend fun getUser(id: Long): Outcome<User>
}

/** The users of [ids] in this map, by id: a lookup per id, not a pass over every user. */
fun Map<Long, User>.pick(ids: Set<Long>): ImmutableMap<Long, User> =
    ids.mapNotNull { id -> get(id)?.let { id to it } }.toMap().toImmutableMap()
