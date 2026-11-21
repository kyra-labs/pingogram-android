package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** The logged-in user [me], Telegram Premium while [premium] is. */
internal class PremiumUsers(
    private val me: Long,
) : UserRepository {
    val premium = MutableStateFlow(false)
    override val myUserId: StateFlow<Long?> = MutableStateFlow(me)
    override val users: Flow<ImmutableMap<Long, User>> = premium.map { persistentMapOf(me to user(it)) }

    override fun user(id: Long): Flow<User?> = premium.map { if (id == me) user(it) else null }

    override suspend fun getUser(id: Long): Outcome<User> = Outcome.Failure(DomainError.NotFound)

    private fun user(premium: Boolean) =
        User(
            id = me,
            firstName = "Ada",
            lastName = "",
            usernames = persistentListOf(),
            phoneNumber = "",
            status = UserStatus.Empty,
            profilePhoto = null,
            isVerified = false,
            isScam = false,
            isFake = false,
            isPremium = premium,
            isContact = false,
            isMutualContact = false,
            isSupport = false,
            type = UserType.Regular,
            haveAccess = true,
        )
}
