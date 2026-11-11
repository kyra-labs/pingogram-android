package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A community: chats gathered under one name and photo (td_api.tl `community`), which a supergroup or channel names in
 * its full info (`GroupDetails.communityId`).
 *
 * @property hasAccess the user may see the community; false for one they were removed from.
 * @property role the user's role in it, with what it lets them do.
 * @property canEditChatList members may suggest chats for the community's list (td_api.tl `communityPermissions`).
 */
data class Community(
    val id: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val hasAccess: Boolean,
    val role: CommunityRole,
    val canEditChatList: Boolean,
) {
    override fun toString() = "Community(id=$id, role=$role, …)"
}

/** The user's role in a community (td_api.tl `CommunityMemberStatus`). */
sealed interface CommunityRole {
    data object Owner : CommunityRole

    /** An administrator: whether they may rename it and change its photo ([canChangeInfo]), and its list of chats. */
    data class Administrator(
        val canChangeInfo: Boolean,
        val canEditChatList: Boolean,
    ) : CommunityRole

    data object Member : CommunityRole

    data object Left : CommunityRole

    data object Banned : CommunityRole
}

/** Whether this role sees the community's hidden chats and changes its name: its owner and administrators. */
val CommunityRole.isAdministrator: Boolean get() = this is CommunityRole.Owner || this is CommunityRole.Administrator

/** Whether this role may rename the community: its owner, and administrators who may change its info. */
val CommunityRole.canChangeInfo: Boolean
    get() = this is CommunityRole.Owner || (this is CommunityRole.Administrator && canChangeInfo)

/**
 * What a community holds beyond its [Community] (td_api.tl `communityFullInfo`), which TDLib asks Telegram for.
 *
 * @property chats the community's chats, in Telegram's order; the hidden ones only for its administrators.
 */
data class CommunityDetails(
    val chats: ImmutableList<CommunityChat>,
    val administratorCount: Int,
)

/**
 * A chat of a community (td_api.tl `communityChat`).
 *
 * @property canViewHistory the user may read the chat before joining it.
 * @property isHidden only the community's administrators see it.
 */
data class CommunityChat(
    val chatId: Long,
    val canViewHistory: Boolean,
    val isHidden: Boolean,
)

/** The limits of a community's name. */
object CommunityLimits {
    /** The longest name, in characters, as Telegram's apps take a chat's title. */
    const val NAME_LENGTH_MAX = 128
}
