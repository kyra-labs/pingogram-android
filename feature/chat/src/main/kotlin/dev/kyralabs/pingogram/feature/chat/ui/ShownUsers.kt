package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

/**
 * The users as a conversation shows them: profiles, which no one's going online changes, with the other side of a
 * private chat ([partnerId], null in groups) live, whose status the header shows. Every user's status change re-ran
 * the whole history before (`docs/PERFORMANCE.md`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun shownUsers(
    partnerId: Flow<Long?>,
    users: UserRepository,
): Flow<ImmutableMap<Long, User>> =
    partnerId.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) {
            users.profiles
        } else {
            combine(users.profiles, users.user(id)) { profiles, partner ->
                if (partner == null) profiles else profiles.toPersistentMap().putting(partner.id, partner)
            }
        }
    }
