package dev.kyralabs.pingogram.feature.chat.ui.topics

import dev.kyralabs.pingogram.core.domain.model.AdministratorRights
import dev.kyralabs.pingogram.core.domain.model.GroupInfo
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.MemberPermissions
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import kotlinx.collections.immutable.persistentListOf

/** The forum as [GroupInfo] for the topics' rights: the user's [role] and the members' [permissions]. */
internal fun forumInfo(
    role: MemberRole = MemberRole.Member,
    permissions: MemberPermissions = MemberPermissions(canSendBasicMessages = true),
) = GroupInfo(
    kind = GroupKind.Supergroup,
    usernames = persistentListOf(),
    memberCount = 12,
    isVerified = false,
    isScam = false,
    isFake = false,
    myRole = role,
    defaultPermissions = permissions,
    details = null,
)

/** An administrator with the right to manage topics, and to delete messages where [canDelete]. */
internal fun topicAdmin(canDelete: Boolean = true) =
    MemberRole.Administrator(
        canBeEdited = false,
        rights = AdministratorRights(canManageTopics = true, canDeleteMessages = canDelete),
    )
