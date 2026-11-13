package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A community: chats gathered under one name and photo (td_api.tl `community`), which a supergroup or channel names in
 * its full info (`GroupDetails.communityId`).
 *
 * @property hasAccess the user may see the community; false for one they were removed from.
 * @property role the user's role in it, with what it lets them do.
 * @property canEditChatList members may suggest chats for the community's list (td_api.tl `communityPermissions`).
 * @property areChatsCollapsed the user shows the community's chats as one entry in the chat list (td_api.tl
 *   `are_chats_collapsed`).
 */
data class Community(
    val id: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val hasAccess: Boolean,
    val role: CommunityRole,
    val canEditChatList: Boolean,
    val areChatsCollapsed: Boolean = false,
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

/** Whether the user is in the community: its owner, an administrator or a member. */
val CommunityRole.isJoined: Boolean
    get() = this is CommunityRole.Owner || this is CommunityRole.Administrator || this == CommunityRole.Member

/**
 * Whether this role adds chats to the community itself (td_api.tl `can_edit_chat_list`): its owner, and administrators
 * with the right; the others send a request administrators approve.
 */
val CommunityRole.canEditChatList: Boolean
    get() = this is CommunityRole.Owner || (this is CommunityRole.Administrator && canEditChatList)

/** Whether this role may rename the community: its owner, and administrators who may change its info. */
val CommunityRole.canChangeInfo: Boolean
    get() = this is CommunityRole.Owner || (this is CommunityRole.Administrator && canChangeInfo)

/**
 * What a community holds beyond its [Community] (td_api.tl `communityFullInfo`), which TDLib asks Telegram for.
 *
 * @property chats the community's chats, in Telegram's order; the hidden ones only for its administrators.
 * @property addChatRequestCount requests to add a chat waiting for its administrators (td_api.tl
 *   `add_chat_request_count`).
 * @property isMuted the user muted the community's chats, which their own settings may still override.
 */
data class CommunityDetails(
    val chats: ImmutableList<CommunityChat>,
    val administratorCount: Int,
    val addChatRequestCount: Int = 0,
    val isMuted: Boolean = false,
)

/**
 * A request to add a chat to a community (td_api.tl `communityAddChatRequest`), which its administrators approve.
 *
 * @property isHidden the chat would be visible to the community's administrators only.
 * @property senderUserId who asked.
 * @property date when, in Unix seconds.
 */
data class CommunityAddChatRequest(
    val chatId: Long,
    val isHidden: Boolean,
    val senderUserId: Long,
    val date: Int,
)

/** What came of adding a chat to a community. */
enum class CommunityAddResult {
    /** The chat is in the community. */
    Added,

    /** The user may not change the list: the community's administrators got a request (`COMMUNITY_REQUEST_CREATED`). */
    RequestSent,
}

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
