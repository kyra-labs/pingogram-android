package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.User
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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

    /** The user with [id], then every change; null while TDLib has not announced the user (see [getUser]). */
    fun user(id: Long): Flow<User?>

    /**
     * The user with [id] from memory, else from TDLib: `DomainError.NotFound` for a user it does not know. A request
     * that gets no answer in time fails with `DomainError.Network`.
     */
    suspend fun getUser(id: Long): Outcome<User>
}
