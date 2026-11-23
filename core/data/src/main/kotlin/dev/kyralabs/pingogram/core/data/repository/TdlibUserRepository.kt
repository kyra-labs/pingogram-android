package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toUser
import dev.kyralabs.pingogram.core.data.mapper.toUserStatus
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.domain.repository.pick
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * [UserRepository] on TDLib's users. TDLib announces each user with `updateUser` before any of its ids reaches the
 * app, and again whenever it changes, so every user is kept for the session: as many as the chats, members and
 * senders TDLib loaded. `updateUserStatus` changes the status of a known user; the `my_id` option names the
 * logged-in user.
 *
 * A user that changed only in fields Pingogram does not keep keeps its map, so [users] emits only real changes.
 */
@AccountScoped
class TdlibUserRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : UserRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, USER_UPDATES, UserCache(), UserCache::reduce)

        override val myUserId: StateFlow<Long?> =
            store.state.map { it.myUserId }.stateIn(scope, SharingStarted.Eagerly, initialValue = null)

        // A new map is a changed user; the my_id option keeps the map.
        override val users: Flow<ImmutableMap<Long, User>> =
            store.state.map { it.users }.distinctUntilChanged { old, new -> old === new }

        // A status change keeps the profiles' map.
        override val profiles: Flow<ImmutableMap<Long, User>> =
            store.state.map { it.profiles }.distinctUntilChanged { old, new -> old === new }

        override fun users(ids: Set<Long>): Flow<ImmutableMap<Long, User>> =
            users.map { all -> all.pick(ids) }.distinctUntilChanged()

        override fun user(id: Long): Flow<User?> = store.state.map { it.users[id] }.distinctUntilChanged()

        override suspend fun getUser(id: Long): Outcome<User> {
            val cached = store.state.value.users[id]
            if (cached != null) return Outcome.Success(cached)
            // The answer is not stored: TDLib sends updateUser before it, and a later update may overtake it here.
            return client.request(TdApi.GetUser(id)).map { it.toUser() }
        }
    }

private data class UserCache(
    val users: PersistentMap<Long, User> = persistentMapOf(),
    val myUserId: Long? = null,
    /** [users] as of each one's last profile change: a status change does not touch it ([UserRepository.profiles]). */
    val profiles: PersistentMap<Long, User> = persistentMapOf(),
) {
    fun reduce(update: TdApi.Update): UserCache =
        when (update) {
            is TdApi.UpdateUser -> {
                withUser(update.user.toUser())
            }

            is TdApi.UpdateUserStatus -> {
                val user = users[update.userId] ?: return this
                val changed = user.copy(status = update.status.toUserStatus())
                if (changed == user) this else copy(users = users.putting(user.id, changed))
            }

            is TdApi.UpdateOption -> {
                if (update.name != MY_ID_OPTION) return this
                // OptionValueEmpty once the option is gone.
                copy(myUserId = (update.value as? TdApi.OptionValueInteger)?.value)
            }

            else -> {
                this
            }
        }

    private fun withUser(user: User): UserCache =
        if (users[user.id] == user) {
            this
        } else {
            // The profile keeps its map while only the status differs from it.
            val profile = profiles[user.id]
            val sameProfile = profile != null && profile.copy(status = user.status) == user
            copy(
                users = users.putting(user.id, user),
                profiles = if (sameProfile) profiles else profiles.putting(user.id, user),
            )
        }
}

private const val MY_ID_OPTION = "my_id"

/** The updates its store's reducer handles. */
private val USER_UPDATES: Set<KClass<out TdApi.Update>> =
    setOf(
        TdApi.UpdateOption::class,
        TdApi.UpdateUser::class,
        TdApi.UpdateUserStatus::class,
    )
