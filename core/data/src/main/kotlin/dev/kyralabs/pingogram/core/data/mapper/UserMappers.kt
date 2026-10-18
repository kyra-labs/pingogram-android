package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

internal fun TdApi.User.toUser(): User {
    val verification = verificationStatus
    return User(
        id = id,
        firstName = firstName.orEmpty(),
        lastName = lastName.orEmpty(),
        usernames = usernames?.activeUsernames.orEmpty().toImmutableList(),
        phoneNumber = phoneNumber.orEmpty(),
        status = status.toUserStatus(),
        profilePhoto = profilePhoto?.toProfilePhoto(),
        isVerified = verification?.isVerified == true,
        isScam = verification?.isScam == true,
        isFake = verification?.isFake == true,
        isPremium = isPremium,
        isContact = isContact,
        isMutualContact = isMutualContact,
        isSupport = isSupport,
        type = type.toUserType(),
        haveAccess = haveAccess,
    )
}

internal fun TdApi.UserStatus?.toUserStatus(): UserStatus =
    when (this) {
        is TdApi.UserStatusOnline -> UserStatus.Online(expiresAtEpochSeconds = expires.toLong())

        is TdApi.UserStatusOffline -> UserStatus.Offline(wasOnlineAtEpochSeconds = wasOnline.toLong())

        is TdApi.UserStatusRecently -> UserStatus.Recently

        is TdApi.UserStatusLastWeek -> UserStatus.LastWeek

        is TdApi.UserStatusLastMonth -> UserStatus.LastMonth

        // UserStatusEmpty, and statuses a newer TDLib may add.
        else -> UserStatus.Empty
    }

private fun TdApi.UserType?.toUserType(): UserType =
    when (this) {
        is TdApi.UserTypeRegular -> UserType.Regular
        is TdApi.UserTypeBot -> UserType.Bot
        is TdApi.UserTypeDeleted -> UserType.Deleted
        else -> UserType.Unknown
    }
