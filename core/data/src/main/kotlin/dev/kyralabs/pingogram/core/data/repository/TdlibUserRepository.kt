package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toUser
import dev.kyralabs.pingogram.core.data.mapper.toUserStatus
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
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
import javax.inject.Singleton

/**
 * [UserRepository] on TDLib's users. TDLib announces each user with `updateUser` before any of its ids reaches the
 * app, and again whenever it changes, so every user is kept for the session: as many as the chats, members and
 * senders TDLib loaded. `updateUserStatus` changes the status of a known user; the `my_id` option names the
 * logged-in user.
 */
@Singleton
class TdlibUserRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : UserRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, UserCache(), UserCache::reduce)

        override val myUserId: StateFlow<Long?> =
            store.state.map { it.myUserId }.stateIn(scope, SharingStarted.Eagerly, initialValue = null)

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
) {
    fun reduce(update: TdApi.Update): UserCache =
        when (update) {
            is TdApi.UpdateUser -> {
                copy(users = users.putting(update.user.id, update.user.toUser()))
            }

            is TdApi.UpdateUserStatus -> {
                val user = users[update.userId] ?: return this
                copy(users = users.putting(user.id, user.copy(status = update.status.toUserStatus())))
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
}

private const val MY_ID_OPTION = "my_id"
