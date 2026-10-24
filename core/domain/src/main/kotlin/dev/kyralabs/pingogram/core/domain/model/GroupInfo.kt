package dev.kyralabs.pingogram.core.domain.model

import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.ImmutableList

/**
 * What the info page of a group or channel shows about it beyond its [Chat] (`REQUIREMENTS.md` FR-P3), from
 * `GroupInfoRepository.groupInfo`: its kind, names and badges, the current user's role in it, and, once TDLib has it,
 * its full info ([details]). Plain data, mapped from TDLib's `basicGroup` or `supergroup` and their full info by
 * `core:data`.
 *
 * `toString()` leaves the usernames and the description out, so it can be logged.
 */
data class GroupInfo(
    val kind: GroupKind,
    /** Active usernames without `@`, the one to show first; empty for a private group or channel and a basic group. */
    val usernames: ImmutableList<String>,
    /** Members (subscribers of a channel) as the group reports them; 0 if unknown, see [GroupDetails.memberCount]. */
    val memberCount: Int,
    val isVerified: Boolean,
    val isScam: Boolean,
    val isFake: Boolean,
    /** The current user's role, with their rights. */
    val myRole: MemberRole,
    /** What members without administrator rights may do (the chat's `permissions`); none in a channel. */
    val defaultPermissions: MemberPermissions,
    /** The full info; null while TDLib has not answered for it, or if it could not be read ([detailsError]). */
    val details: GroupDetails?,
    /** Why the full info could not be read; null if it was, or is still on its way. */
    val detailsError: DomainError? = null,
    /** How many people asked to join and wait for an administrator (the chat's `pending_join_requests`). */
    val pendingJoinRequests: Int = 0,
) {
    /** The rights the current user has here: every right as the owner, none as anyone but an administrator. */
    val myRights: AdministratorRights get() = myRole.rights

    override fun toString() = "GroupInfo(kind=$kind, myRole=$myRole, details=$details, …)"
}

/** What kind of group a [GroupInfo] is about. */
enum class GroupKind {
    /** A small group (TDLib's `basicGroup`), without public usernames, banned users or slow mode. */
    BasicGroup,

    /** A supergroup. */
    Supergroup,

    /** A channel, where only administrators post. */
    Channel,
}

/**
 * The full info of a group or channel (td_api.tl `basicGroupFullInfo`, `supergroupFullInfo`): what TDLib asks
 * Telegram for once and then keeps up to date.
 *
 * @property description the description with the links, mentions and hashtags in it.
 * @property memberCount the members (subscribers); for a basic group, those of [members].
 * @property canGetMembers the current user may list the members: a channel's subscribers only for its administrators,
 *   a group's members unless they are hidden ([hasHiddenMembers]) from non-administrators.
 * @property hasHiddenMembers the group hides its members from non-administrators.
 * @property slowModeDelay seconds a member must wait between messages; 0 without slow mode (supergroups only).
 * @property members a basic group's members, which its full info holds all of; empty for supergroups and channels,
 *   whose members are paged (`GroupInfoRepository.members`).
 * @property primaryInviteLink the primary invite link, for administrators who may invite; null otherwise.
 */
data class GroupDetails(
    val description: FormattedText,
    val memberCount: Int,
    val administratorCount: Int,
    val restrictedCount: Int,
    val bannedCount: Int,
    val canGetMembers: Boolean,
    val hasHiddenMembers: Boolean,
    val slowModeDelay: Int,
    val members: ImmutableList<GroupMember>,
    val primaryInviteLink: InviteLink?,
) {
    override fun toString() =
        "GroupDetails(memberCount=$memberCount, administratorCount=$administratorCount, members=${members.size}, …)"
}

/**
 * A member of a group or channel (td_api.tl `chatMember`): a user, or a chat (a channel posting in a group, a banned
 * channel).
 *
 * @property tag the member's tag, which administrators give (td_api.tl `chatMember.tag`, the former custom title of
 *   administrators); empty without one.
 * @property joinedDate when the member joined, a Unix timestamp; 0 if unknown.
 */
data class GroupMember(
    val sender: MessageSender,
    val role: MemberRole,
    val tag: String = "",
    val joinedDate: Long = 0,
) {
    override fun toString() = "GroupMember(sender=$sender, role=$role, …)"
}

/** A page of members, and how many there are in all for the filter asked for. */
data class GroupMembersPage(
    val members: ImmutableList<GroupMember>,
    val totalCount: Int,
)

/** Which members `GroupInfoRepository.members` lists. */
enum class MemberFilter {
    /** Every member, the most recent first; with a query, those whose names match it. */
    Recent,

    /** The owner and the administrators. */
    Administrators,

    /** Members with restrictions, who may do less than others (supergroups only). */
    Restricted,

    /** Users and chats removed and kept from coming back (supergroups and channels only). */
    Banned,
}

/** A member's role in a group or channel, with its rights (td_api.tl `ChatMemberStatus`, every field). */
sealed interface MemberRole {
    /** Whether the member is in the group or channel. */
    val isMember: Boolean

    /** The owner, with every right; one who left stays the owner. */
    data class Owner(
        override val isMember: Boolean,
        val isAnonymous: Boolean = false,
    ) : MemberRole

    /**
     * An administrator with [rights]; [canBeEdited] when the current user may change them (they promoted them, or
     * own the group).
     */
    data class Administrator(
        val canBeEdited: Boolean,
        val rights: AdministratorRights,
    ) : MemberRole {
        override val isMember = true
    }

    data object Member : MemberRole {
        override val isMember = true
    }

    /**
     * A member who may do only what [permissions] allow, until [untilDate] (a Unix timestamp; 0 for ever); also a
     * former member who stays restricted on return ([isMember] false).
     */
    data class Restricted(
        override val isMember: Boolean,
        val untilDate: Long,
        val permissions: MemberPermissions,
    ) : MemberRole

    data object Left : MemberRole {
        override val isMember = false
    }

    /** Removed and kept from coming back until [untilDate] (a Unix timestamp; 0 for ever). */
    data class Banned(
        val untilDate: Long,
    ) : MemberRole {
        override val isMember = false
    }
}

/** The rights [this] role gives: every right for the owner, an administrator's own, none for anyone else. */
val MemberRole.rights: AdministratorRights
    get() =
        when (this) {
            is MemberRole.Owner -> AdministratorRights.All.copy(isAnonymous = isAnonymous)
            is MemberRole.Administrator -> rights
            else -> AdministratorRights.None
        }

/** Whether [this] role is the owner's or an administrator's. */
val MemberRole.isAdministrator: Boolean get() = this is MemberRole.Owner || this is MemberRole.Administrator

/**
 * An administrator's rights (td_api.tl `chatAdministratorRights`, every field, so changing one keeps the others).
 * Some apply to channels only ([canPostMessages], [canEditMessages], [canManageDirectMessages]), some to groups only
 * ([canPinMessages], [canManageTopics], [canManageTags], [canSendWelcomeMessages], [isAnonymous]).
 */
data class AdministratorRights(
    val canManageChat: Boolean = false,
    val canChangeInfo: Boolean = false,
    val canPostMessages: Boolean = false,
    val canEditMessages: Boolean = false,
    val canDeleteMessages: Boolean = false,
    val canInviteUsers: Boolean = false,
    val canRestrictMembers: Boolean = false,
    val canPinMessages: Boolean = false,
    val canManageTopics: Boolean = false,
    val canPromoteMembers: Boolean = false,
    val canManageVideoChats: Boolean = false,
    val canPostStories: Boolean = false,
    val canEditStories: Boolean = false,
    val canDeleteStories: Boolean = false,
    val canManageDirectMessages: Boolean = false,
    val canManageTags: Boolean = false,
    val canSendWelcomeMessages: Boolean = false,
    val isAnonymous: Boolean = false,
) {
    companion object {
        val None = AdministratorRights()

        /** Every right, as the owner has, without anonymity. */
        val All =
            AdministratorRights(
                canManageChat = true,
                canChangeInfo = true,
                canPostMessages = true,
                canEditMessages = true,
                canDeleteMessages = true,
                canInviteUsers = true,
                canRestrictMembers = true,
                canPinMessages = true,
                canManageTopics = true,
                canPromoteMembers = true,
                canManageVideoChats = true,
                canPostStories = true,
                canEditStories = true,
                canDeleteStories = true,
                canManageDirectMessages = true,
                canManageTags = true,
                canSendWelcomeMessages = true,
            )

        /**
         * The rights a member promoted to administrator starts with, as the official apps offer them: managing the
         * group, changing its info, deleting messages, inviting, restricting, pinning and managing video chats; posting
         * and editing in a channel.
         */
        fun defaultsFor(kind: GroupKind) =
            AdministratorRights(
                canManageChat = true,
                canChangeInfo = true,
                canPostMessages = kind == GroupKind.Channel,
                canEditMessages = kind == GroupKind.Channel,
                canDeleteMessages = true,
                canInviteUsers = true,
                canRestrictMembers = kind != GroupKind.Channel,
                canPinMessages = kind != GroupKind.Channel,
                canManageVideoChats = true,
            )
    }
}

/**
 * What members may do (td_api.tl `chatPermissions`, every field, so changing one keeps the others): the group's
 * default permissions, or what a restricted member may still do.
 */
data class MemberPermissions(
    val canSendBasicMessages: Boolean = false,
    val canSendAudios: Boolean = false,
    val canSendDocuments: Boolean = false,
    val canSendPhotos: Boolean = false,
    val canSendVideos: Boolean = false,
    val canSendVideoNotes: Boolean = false,
    val canSendVoiceNotes: Boolean = false,
    val canSendPolls: Boolean = false,
    val canSendOtherMessages: Boolean = false,
    val canAddLinkPreviews: Boolean = false,
    val canReactToMessages: Boolean = false,
    val canEditTag: Boolean = false,
    val canChangeInfo: Boolean = false,
    val canInviteUsers: Boolean = false,
    val canPinMessages: Boolean = false,
    val canCreateTopics: Boolean = false,
) {
    companion object {
        val None = MemberPermissions()
    }
}

/**
 * The slow mode delays Telegram accepts, in seconds (td_api.tl `setChatSlowModeDelay`): off, then 5 s up to an hour.
 */
val SlowModeDelays: List<Int> = listOf(0, 5, 10, 30, 60, 300, 900, 3600)

/**
 * An invite link of a group or channel (td_api.tl `chatInviteLink`). Times are Unix timestamps in seconds.
 *
 * @property link the link itself, `https://t.me/+…`.
 * @property name the name its creator gave it; empty without one.
 * @property expirationDate when it stops working; 0 if it never does.
 * @property memberLimit how many may join through it; 0 without a limit.
 * @property memberCount how many joined through it.
 * @property createsJoinRequest joining through it asks the administrators first.
 * @property isPrimary the group's primary link, which can be replaced but not edited.
 * @property isSubscription a paid subscription link, which Pingogram shows but does not edit.
 *
 * `toString()` leaves the link and its name out.
 */
data class InviteLink(
    val link: String,
    val name: String,
    val creatorUserId: Long,
    val date: Long,
    val expirationDate: Long,
    val memberLimit: Int,
    val memberCount: Int,
    val pendingJoinRequestCount: Int,
    val createsJoinRequest: Boolean,
    val isPrimary: Boolean,
    val isRevoked: Boolean,
    val isSubscription: Boolean = false,
) {
    /** It still works at [nowEpochSeconds]: not revoked, not expired and not full. */
    fun isActiveAt(nowEpochSeconds: Long): Boolean =
        !isRevoked &&
            (expirationDate == 0L || expirationDate > nowEpochSeconds) &&
            (memberLimit == 0 || memberCount < memberLimit)

    override fun toString() = "InviteLink(isPrimary=$isPrimary, isRevoked=$isRevoked, memberCount=$memberCount, …)"
}

/** A page of invite links, newest first, and how many there are in all. */
data class InviteLinksPage(
    val links: ImmutableList<InviteLink>,
    val totalCount: Int,
)

/**
 * What a new or edited invite link is: its [name] (up to 32 characters, may be empty), when it expires (a Unix
 * timestamp; 0 never), how many may join (0 without a limit; at most 99,999, and none with [createsJoinRequest]).
 */
data class InviteLinkSettings(
    val name: String = "",
    val expirationDate: Long = 0,
    val memberLimit: Int = 0,
    val createsJoinRequest: Boolean = false,
) {
    override fun toString() =
        "InviteLinkSettings(expirationDate=$expirationDate, memberLimit=$memberLimit, " +
            "createsJoinRequest=$createsJoinRequest, …)"

    companion object {
        /** The longest name Telegram keeps for a link. */
        const val NAME_LENGTH_MAX = 32

        /** The largest member limit Telegram accepts. */
        const val MEMBER_LIMIT_MAX = 99_999
    }
}
