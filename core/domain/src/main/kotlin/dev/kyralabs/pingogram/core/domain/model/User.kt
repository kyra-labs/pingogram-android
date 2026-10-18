package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A Telegram user as TDLib knows it (`UserRepository`): the current user, contacts, chat partners and senders.
 *
 * `toString()` leaves names, usernames and the phone number out, so a user can be logged.
 */
data class User(
    val id: Long,
    val firstName: String,
    /** Empty if the user has none. */
    val lastName: String,
    /** Active usernames without `@`, the one to show first; empty if the user has none. */
    val usernames: ImmutableList<String>,
    /** Digits without `+`, as TDLib reports it; empty when the user's privacy settings hide it. */
    val phoneNumber: String,
    val status: UserStatus,
    /** Null if the user has no photo, or hides it from the current user. */
    val profilePhoto: ProfilePhoto?,
    /** Verified by Telegram; shown as a badge. */
    val isVerified: Boolean,
    /** Many users reported the account as a scam; shown as a warning badge. */
    val isScam: Boolean,
    /** Many users reported the account as impersonating someone; shown as a warning badge. */
    val isFake: Boolean,
    val isPremium: Boolean,
    /** In the current user's contacts. */
    val isContact: Boolean,
    /** In the current user's contacts, and the current user is in theirs. */
    val isMutualContact: Boolean,
    /** A Telegram support account. */
    val isSupport: Boolean,
    val type: UserType,
    /** False if TDLib knows nothing more than this about the user and accepts its id in no request. */
    val haveAccess: Boolean,
) {
    override fun toString() = "User(id=$id, type=$type, status=$status, …)"
}

/** When a user was last online, as far as their privacy settings tell (td_api.tl `UserStatus`). */
sealed interface UserStatus {
    /** Never online, or it does not apply (bots, deleted accounts). */
    data object Empty : UserStatus

    /**
     * Online until [expiresAtEpochSeconds] (Unix time). TDLib reports no change when that time passes; from then on
     * the user counts as offline ([isOnlineAt]).
     */
    data class Online(
        val expiresAtEpochSeconds: Long,
    ) : UserStatus

    /** Last online at [wasOnlineAtEpochSeconds] (Unix time). */
    data class Offline(
        val wasOnlineAtEpochSeconds: Long,
    ) : UserStatus

    /** Online recently; the user's privacy settings hide the exact time. */
    data object Recently : UserStatus

    /** Online within the last week; the exact time is hidden. */
    data object LastWeek : UserStatus

    /** Online within the last month; the exact time is hidden. */
    data object LastMonth : UserStatus
}

/** Whether the user is online at [nowEpochSeconds] (Unix time): [UserStatus.Online] holds only until it expires. */
fun UserStatus.isOnlineAt(nowEpochSeconds: Long): Boolean =
    this is UserStatus.Online && expiresAtEpochSeconds > nowEpochSeconds

/** What kind of account a [User] is. */
enum class UserType {
    Regular,
    Bot,

    /** A deleted account: no name, photo or status. */
    Deleted,

    /** TDLib knows nothing but the id (rare); shown like [Deleted]. */
    Unknown,
}
